// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.inspections.dfa

import com.intellij.codeInspection.dataFlow.jvm.descriptors.JvmVariableDescriptor
import com.intellij.codeInspection.dataFlow.types.DfType
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue
import com.intellij.psi.PsiElement
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.builtins.getReceiverTypeFromFunctionType
import org.jetbrains.kotlin.builtins.getValueParameterTypesFromFunctionType
import org.jetbrains.kotlin.descriptors.ValueParameterDescriptor
import org.jetbrains.kotlin.idea.caches.resolve.resolveMainReference
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptorIfAny
import org.jetbrains.kotlin.idea.core.isOverridable
import org.jetbrains.kotlin.idea.core.resolveType
import org.jetbrains.kotlin.idea.refactoring.move.moveMethod.type
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.references.readWriteAccess
import org.jetbrains.kotlin.idea.references.resolveMainReferenceToDescriptors
import org.jetbrains.kotlin.idea.util.findAnnotation
import org.jetbrains.kotlin.load.kotlin.toSourceElement
import org.jetbrains.kotlin.name.JvmStandardClassIds.VOLATILE_ANNOTATION_FQ_NAME
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.isExtensionDeclaration
import org.jetbrains.kotlin.psi.psiUtil.parents
import org.jetbrains.kotlin.resolve.source.KotlinSourceElement
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.util.match

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

    override fun getPsiElement(): KtCallableDeclaration = variable

    override fun getDfType(qualifier: DfaVariableValue?): DfType = variable.type().toDfType()

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
            val descriptor = KtLambdaSpecialVariableDescriptor(lambda.functionLiteral, LambdaVariableKind.IT, kotlinType)
            return factory.varFactory.createVariableValue(descriptor)
        }
        
        fun getLambdaReceiver(factory: DfaValueFactory, lambda: KtLambdaExpression): DfaVariableValue? {
            val receiverType = lambda.resolveType()?.getReceiverTypeFromFunctionType() ?: return null
            val descriptor = KtLambdaSpecialVariableDescriptor(lambda.functionLiteral, LambdaVariableKind.THIS, receiverType)
            return factory.varFactory.createVariableValue(descriptor)
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
                val target = expr.resolveMainReference()
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
                        if (target.parents.match(KtClassBody::class, last = KtObjectDeclaration::class) != null) {
                            // property in object: singleton, can track
                            return varFactory.createVariableValue(KtVariableDescriptor(target), null)
                        }
                        if (parent is KtQualifiedExpression && parent.selectorExpression == expr) {
                            val receiver = parent.receiverExpression
                            qualifier = createFromSimpleName(factory, receiver)
                        } else {
                            if (target.parent is KtFile) {
                                // top-level declaration
                                return varFactory.createVariableValue(KtVariableDescriptor(target), null)
                            }
                            val classOrObject = target.containingClassOrObject?.resolveToDescriptorIfAny()
                            if (classOrObject != null) {
                                val dfType = classOrObject.defaultType.toDfType()
                                qualifier = varFactory.createVariableValue(KtThisDescriptor(classOrObject, dfType))
                            }
                        }
                        if (qualifier != null) {
                            return varFactory.createVariableValue(KtVariableDescriptor(target), qualifier)
                        }
                    }
                }
                if (expr.textMatches(StandardNames.IMPLICIT_LAMBDA_PARAMETER_NAME.identifier)) {
                    val descriptor = expr.resolveMainReferenceToDescriptors().singleOrNull()
                    if (descriptor is ValueParameterDescriptor) {
                        val fn = (descriptor.containingDeclaration.toSourceElement as? KotlinSourceElement)?.psi
                        if (fn is KtFunctionLiteral) {
                            val type = descriptor.type
                            return varFactory.createVariableValue(KtLambdaSpecialVariableDescriptor(fn, LambdaVariableKind.IT, type))
                        }
                    }
                }
            }
            return null
        }

        private fun isTrackableProperty(target: PsiElement?) =
            target is KtParameter && target.ownerFunction is KtPrimaryConstructor ||
            target is KtProperty && !target.hasDelegate() && target.getter == null && target.setter == null &&
                    !target.isOverridable && !target.isExtensionDeclaration() &&
                    target.findAnnotation(VOLATILE_ANNOTATION_FQ_NAME) == null
    }
}
enum class LambdaVariableKind { IT, THIS }

class KtLambdaSpecialVariableDescriptor(val lambda: KtFunctionLiteral, val kind: LambdaVariableKind, val type: KotlinType): JvmVariableDescriptor() {
    override fun getDfType(qualifier: DfaVariableValue?): DfType = type.toDfType()
    override fun isStable(): Boolean = true
    override fun equals(other: Any?): Boolean = other is KtLambdaSpecialVariableDescriptor && other.lambda == lambda && other.kind == kind
    override fun hashCode(): Int = lambda.hashCode() * 31 + kind.hashCode()
    override fun toString(): String = kind.toString().lowercase()
}
