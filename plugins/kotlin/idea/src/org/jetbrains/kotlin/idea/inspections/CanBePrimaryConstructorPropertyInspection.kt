// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.inspections

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.kotlin.descriptors.ClassConstructorDescriptor
import org.jetbrains.kotlin.descriptors.ValueParameterDescriptor
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.idea.codeinsights.impl.base.hasUsages
import org.jetbrains.kotlin.idea.inspections.CanBePrimaryConstructorPropertyUtils.getCorrespondingPrimaryConstructorParameter
import org.jetbrains.kotlin.idea.intentions.MovePropertyToConstructorIntention
import org.jetbrains.kotlin.idea.refactoring.isInterfaceClass
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.DescriptorToSourceUtils
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull

class CanBePrimaryConstructorPropertyInspection : AbstractKotlinInspection() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = propertyVisitor(fun(property) {
        val nameIdentifier = property.nameIdentifier ?: return
        val parameter = property.getCorrespondingPrimaryConstructorParameter() ?: return

        holder.registerProblem(
            holder.manager.createProblemDescriptor(
                nameIdentifier,
                nameIdentifier,
                KotlinBundle.message("property.is.explicitly.assigned.to.parameter.0.can", parameter.name),
                ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                isOnTheFly,
                MovePropertyToConstructorIntention()
            )
        )
    })
}

internal object CanBePrimaryConstructorPropertyUtils {
    fun KtProperty.canBePrimaryConstructorProperty(): Boolean =
        getCorrespondingPrimaryConstructorParameter() != null

    fun KtProperty.getCorrespondingPrimaryConstructorParameter(): ValueParameterDescriptor? {
        if (isLocal) return null
        if (getter != null || setter != null || delegate != null) return null

        val reference = initializer as? KtReferenceExpression ?: return null
        val context = reference.analyze()
        val assignedDescriptor = context.get(BindingContext.REFERENCE_TARGET, reference) as? ValueParameterDescriptor ?: return null

        val containingConstructor = assignedDescriptor.containingDeclaration as? ClassConstructorDescriptor ?: return null
        if (containingConstructor.containingDeclaration.isData) return null

        val propertyTypeReference = typeReference
        val propertyType = context.get(BindingContext.TYPE, propertyTypeReference)
        if (propertyType != null && propertyType != assignedDescriptor.type) return null

        val nameIdentifier = nameIdentifier ?: return null
        if (nameIdentifier.text != assignedDescriptor.name.asString()) return null

        val assignedParameter = DescriptorToSourceUtils.descriptorToDeclaration(assignedDescriptor) as? KtParameter ?: return null
        val containingClassOrObject = containingClassOrObject ?: return null
        if (containingClassOrObject !== assignedParameter.containingClassOrObject) return null
        if (containingClassOrObject.isInterfaceClass()) return null
        if (hasModifier(KtTokens.OPEN_KEYWORD)
            && containingClassOrObject is KtClass
            && containingClassOrObject.isOpen()
            && assignedParameter.isUsedInClassInitializer(containingClassOrObject)
        ) return null

        return assignedDescriptor
    }

    private fun KtClass.isOpen(): Boolean {
        return hasModifier(KtTokens.OPEN_KEYWORD) || hasModifier(KtTokens.ABSTRACT_KEYWORD) || hasModifier(KtTokens.SEALED_KEYWORD)
    }

    private fun KtParameter.isUsedInClassInitializer(containingClass: KtClass): Boolean {
        val classInitializer = containingClass.body?.declarations?.firstIsInstanceOrNull<KtClassInitializer>() ?: return false
        return hasUsages(classInitializer)
    }
}