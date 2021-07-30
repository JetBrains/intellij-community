// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.inspections.dfa

import com.intellij.codeInspection.dataFlow.types.DfType
import com.intellij.codeInspection.dataFlow.value.DfaValue
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue
import com.intellij.codeInspection.dataFlow.value.VariableDescriptor
import com.intellij.psi.PsiElement
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptorIfAny
import org.jetbrains.kotlin.idea.refactoring.move.moveMethod.type
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.references.readWriteAccess
import org.jetbrains.kotlin.idea.util.findAnnotation
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClass
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.isExtensionDeclaration
import org.jetbrains.kotlin.resolve.jvm.annotations.VOLATILE_ANNOTATION_FQ_NAME

class KtVariableDescriptor(val variable: KtCallableDeclaration) : VariableDescriptor {
    val stable: Boolean = calculateStable()

    private fun calculateStable(): Boolean {
        if (variable is KtParameter && variable.isMutable) return false
        if (variable !is KtProperty || !variable.isVar) return true
        if (!variable.isLocal) return false
        return getVariablesChangedInLambdas(variable.parent).contains(variable)
    }

    private fun getVariablesChangedInLambdas(parent: PsiElement): Set<KtProperty> =
        CachedValuesManager.getProjectPsiDependentCache(parent) { scope ->
            val result = hashSetOf<KtProperty>()
            PsiTreeUtil.processElements(scope) { e ->
                if (e is KtSimpleNameExpression && e.readWriteAccess(false).isWrite) {
                    val target = e.mainReference.resolve()
                    if (target is KtProperty && target.isLocal && PsiTreeUtil.isAncestor(parent, target, true)) {
                        val parentLambda = PsiTreeUtil.getParentOfType(parent, KtLambdaExpression::class.java)
                        if (parentLambda != null && PsiTreeUtil.isAncestor(parent, parentLambda, true)) {
                            result.add(target)
                        }
                    }
                }
                return@processElements true
            }
            return@getProjectPsiDependentCache result
        }

    override fun isStable(): Boolean = stable

    override fun getDfType(qualifier: DfaVariableValue?): DfType = variable.type().toDfType(variable)

    override fun createValue(factory: DfaValueFactory, qualifier: DfaValue?): DfaValue {
        assert(qualifier == null) { "Local variable descriptor should not be qualified, got qualifier '$qualifier'" }
        return factory.varFactory.createVariableValue(this)
    }

    override fun equals(other: Any?): Boolean = other is KtVariableDescriptor && other.variable == variable

    override fun hashCode(): Int = variable.hashCode()

    override fun toString(): String = variable.name ?: "<unknown>"
    
    companion object {
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