// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.inspections.dfa

import com.intellij.codeInspection.dataFlow.jvm.descriptors.JvmVariableDescriptor
import com.intellij.codeInspection.dataFlow.types.DfType
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue
import com.intellij.psi.PsiElement
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.builtins.getValueParameterTypesFromFunctionType
import org.jetbrains.kotlin.descriptors.DeclarationDescriptorWithSource
import org.jetbrains.kotlin.descriptors.ValueParameterDescriptor
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptorIfAny
import org.jetbrains.kotlin.idea.core.resolveType
import org.jetbrains.kotlin.idea.refactoring.move.moveMethod.type
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.references.readWriteAccess
import org.jetbrains.kotlin.idea.references.resolveMainReferenceToDescriptors
import org.jetbrains.kotlin.idea.util.findAnnotation
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClass
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.isExtensionDeclaration
import org.jetbrains.kotlin.resolve.jvm.annotations.VOLATILE_ANNOTATION_FQ_NAME
import org.jetbrains.kotlin.resolve.source.KotlinSourceElement
import org.jetbrains.kotlin.types.KotlinType

class KtVariableDescriptor(val variable: KtCallableDeclaration) : JvmVariableDescriptor() {
    val stable: Boolean = calculateStable()

    private fun calculateStable(): Boolean {
        if (variable is KtParameter && variable.isMutable) return false
        if (variable !is KtProperty || !variable.isVar) return true
        if (!variable.isLocal) return false
        return !getVariablesChangedInNestedFunctions(variable.parent).contains(variable)
    }

    private fun getVariablesChangedInNestedFunctions(parent: PsiElement): Set<KtProperty> =
        CachedValuesManager.getProjectPsiDependentCache(parent) { scope ->
            val result = hashSetOf<KtProperty>()
            PsiTreeUtil.processElements(scope) { e ->
                if (e is KtSimpleNameExpression && e.readWriteAccess(false).isWrite) {
                    val target = e.mainReference.resolve()
                    if (target is KtProperty && target.isLocal && PsiTreeUtil.isAncestor(parent, target, true)) {
                        var parentScope : KtFunction?
                        var context = e
                        while(true) {
                            parentScope = PsiTreeUtil.getParentOfType(context, KtFunction::class.java)
                            val maybeLambda = parentScope?.parent as? KtLambdaExpression
                            val maybeCall = (maybeLambda?.parent as? KtLambdaArgument)?.parent as? KtCallExpression
                            if (maybeCall != null && getInlineableLambda(maybeCall)?.lambda == maybeLambda) {
                                context = maybeCall
                                continue
                            }
                            break
                        }
                        if (parentScope != null && PsiTreeUtil.isAncestor(parent, parentScope, true)) {
                            result.add(target)
                        }
                    }
                }
                return@processElements true
            }
            return@getProjectPsiDependentCache result
        }

    override fun isStable(): Boolean = stable

    override fun canBeCapturedInClosure(): Boolean {
        if (variable is KtParameter && variable.isMutable) return false
        return variable !is KtProperty || !variable.isVar
    }

    override fun getDfType(qualifier: DfaVariableValue?): DfType = variable.type().toDfType(variable)

    override fun equals(other: Any?): Boolean = other is KtVariableDescriptor && other.variable == variable

    override fun hashCode(): Int = variable.hashCode()

    override fun toString(): String = variable.name ?: "<unknown>"
    
    companion object {
        fun getSingleLambdaParameter(factory: DfaValueFactory, lambda: KtLambdaExpression): DfaVariableValue? {
            val parameters = lambda.valueParameters
            if (parameters.size > 1) return null
            if (parameters.size == 1) {
                return if (parameters[0].destructuringDeclaration == null)
                    factory.varFactory.createVariableValue(KtVariableDescriptor(parameters[0]))
                else null
            }
            val kotlinType = lambda.resolveType()?.getValueParameterTypesFromFunctionType()?.singleOrNull()?.type ?: return null
            return factory.varFactory.createVariableValue(KtItVariableDescriptor(lambda.functionLiteral, kotlinType))
        }

        fun createFromQualified(factory: DfaValueFactory, expr: KtExpression?): DfaVariableValue? {
            var selector = expr
            while (selector is KtQualifiedExpression) {
                selector = selector.selectorExpression
            }
            return createFromSimpleName(factory, selector)
        }

        fun createFromSimpleName(factory: DfaValueFactory, expr: KtExpression?): DfaVariableValue? {
            val varFactory = factory.varFactory
            if (expr is KtSimpleNameExpression) {
                val target = expr.mainReference.resolve()
                if (target is KtCallableDeclaration) {
                    if (target is KtParameter && target.ownerFunction !is KtPrimaryConstructor ||
                        target is KtProperty && target.isLocal ||
                        target is KtDestructuringDeclarationEntry
                    ) {
                        return varFactory.createVariableValue(KtVariableDescriptor(target))
                    }
                    if (isTrackableProperty(target)) {
                        val parent = expr.parent
                        var qualifier: DfaVariableValue? = null
                        if (parent is KtQualifiedExpression && parent.selectorExpression == expr) {
                            val receiver = parent.receiverExpression
                            qualifier = createFromSimpleName(factory, receiver)
                        } else {
                            val classOrObject = target.containingClassOrObject?.resolveToDescriptorIfAny()
                            if (classOrObject != null) {
                                val dfType = classOrObject.defaultType.toDfType(expr)
                                qualifier = varFactory.createVariableValue(KtThisDescriptor(classOrObject, dfType))
                            }
                        }
                        if (qualifier != null) {
                            return varFactory.createVariableValue(KtVariableDescriptor(target), qualifier)
                        }
                    }
                }
                if (expr.textMatches("it")) {
                    val descriptor = expr.resolveMainReferenceToDescriptors().singleOrNull()
                    if (descriptor is ValueParameterDescriptor) {
                        val fn = ((descriptor.containingDeclaration as? DeclarationDescriptorWithSource)?.source as? KotlinSourceElement)?.psi
                        if (fn != null) {
                            val type = descriptor.type
                            return varFactory.createVariableValue(KtItVariableDescriptor(fn, type))
                        }
                    }
                }
            }
            return null
        }

        private fun isTrackableProperty(target: PsiElement?) =
            target is KtParameter && target.ownerFunction is KtPrimaryConstructor ||
            target is KtProperty && !target.hasDelegate() && target.getter == null && target.setter == null &&
                    !target.hasModifier(KtTokens.ABSTRACT_KEYWORD) &&
                    target.findAnnotation(VOLATILE_ANNOTATION_FQ_NAME) == null &&
                    target.containingClass()?.isInterface() != true &&
                    !target.isExtensionDeclaration()
    }
}
class KtItVariableDescriptor(val lambda: KtElement, val type: KotlinType): JvmVariableDescriptor() {
    override fun getDfType(qualifier: DfaVariableValue?): DfType = type.toDfType(lambda)
    override fun isStable(): Boolean = true
    override fun equals(other: Any?): Boolean = other is KtItVariableDescriptor && other.lambda == lambda
    override fun hashCode(): Int = lambda.hashCode()
    override fun toString(): String = "it"
}
