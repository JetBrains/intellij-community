// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.intentions

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiRecursiveVisitor
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.descriptors.ParameterDescriptor
import org.jetbrains.kotlin.descriptors.PropertyDescriptor
import org.jetbrains.kotlin.descriptors.annotations.AnnotationUseSiteTarget
import org.jetbrains.kotlin.descriptors.annotations.KotlinTarget
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.caches.resolve.resolveToCall
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptorIfAny
import org.jetbrains.kotlin.idea.codeinsight.api.classic.intentions.SelfTargetingIntention
import org.jetbrains.kotlin.idea.codeinsights.impl.base.intentions.MovePropertyToConstructorUtils.buildAdditionalConstructorParameterText
import org.jetbrains.kotlin.idea.codeinsights.impl.base.intentions.MovePropertyToConstructorUtils.buildReplacementConstructorParameterText
import org.jetbrains.kotlin.idea.codeinsights.impl.base.intentions.MovePropertyToConstructorUtils.isMovableToConstructorByPsi
import org.jetbrains.kotlin.idea.core.ShortenReferences
import org.jetbrains.kotlin.idea.intentions.MovePropertyToConstructorUtils.canMoveToConstructor
import org.jetbrains.kotlin.idea.intentions.MovePropertyToConstructorUtils.moveToConstructor
import org.jetbrains.kotlin.idea.search.usagesSearch.descriptor
import org.jetbrains.kotlin.idea.util.CommentSaver
import org.jetbrains.kotlin.idea.util.IdeDescriptorRenderers
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.resolve.AnnotationChecker
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.resolve.source.getPsi
import org.jetbrains.kotlin.types.KotlinType

class MovePropertyToConstructorIntention :
  SelfTargetingIntention<KtProperty>(KtProperty::class.java, KotlinBundle.messagePointer("move.to.constructor")),
  LocalQuickFix {

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val property = descriptor.psiElement as? KtProperty ?: return
        applyTo(property, null)
    }

    override fun isApplicableTo(element: KtProperty, caretOffset: Int): Boolean = element.canMoveToConstructor()

    override fun applyTo(element: KtProperty, editor: Editor?) {
        element.moveToConstructor()
    }
}

internal object MovePropertyToConstructorUtils {
    fun KtProperty.canMoveToConstructor(): Boolean =
        isMovableToConstructorByPsi() && (initializer?.isValidInConstructor() ?: true)

    fun KtProperty.moveToConstructor() {
        val parentClass = PsiTreeUtil.getParentOfType(this, KtClass::class.java) ?: return
        val psiFactory = KtPsiFactory(project)
        val primaryConstructor = parentClass.createPrimaryConstructorIfAbsent()
        val constructorParameter = findConstructorParameter()

        val commentSaver = CommentSaver(this)

        val context = analyze(BodyResolveMode.PARTIAL)
        val propertyAnnotationsText = modifierList?.annotationEntries?.joinToString(separator = " ") {
            it.getTextWithUseSite(context)
        }

        if (constructorParameter != null) {
            val parameterText = buildReplacementConstructorParameterText(constructorParameter, propertyAnnotationsText)
            constructorParameter.replace(psiFactory.createParameter(parameterText)).apply {
                commentSaver.restore(this)
            }
        } else {
            val typeText = typeReference?.text
                ?: (resolveToDescriptorIfAny() as? PropertyDescriptor)?.type?.render()
                ?: return

            val parameterText = buildAdditionalConstructorParameterText(typeText, propertyAnnotationsText)

            primaryConstructor.valueParameterList?.addParameter(psiFactory.createParameter(parameterText))?.apply {
                ShortenReferences.DEFAULT.process(this)
                commentSaver.restore(this)
            }
        }

        delete()
    }

    private fun KtProperty.findConstructorParameter(): KtParameter? {
        val reference = initializer as? KtReferenceExpression ?: return null
        val parameterDescriptor = reference.resolveToCall()?.resultingDescriptor as? ParameterDescriptor ?: return null
        return parameterDescriptor.source.getPsi() as? KtParameter
    }

    private fun KtAnnotationEntry.getTextWithUseSite(context: BindingContext): String {
        if (useSiteTarget != null) return text
        val typeReference = this.typeReference?.text ?: return text
        val valueArgumentList = valueArgumentList?.text.orEmpty()

        fun AnnotationUseSiteTarget.textWithMe() = "@$renderName:$typeReference$valueArgumentList"

        val descriptor = context[BindingContext.ANNOTATION, this] ?: return text
        val applicableTargets = AnnotationChecker.applicableTargetSet(descriptor)
        return when {
            KotlinTarget.VALUE_PARAMETER !in applicableTargets ->
                text
            KotlinTarget.PROPERTY in applicableTargets ->
                AnnotationUseSiteTarget.PROPERTY.textWithMe()
            KotlinTarget.FIELD in applicableTargets ->
                AnnotationUseSiteTarget.FIELD.textWithMe()
            else ->
                text
        }
    }

    private fun KotlinType.render() = IdeDescriptorRenderers.SOURCE_CODE.renderType(this)

    private fun KtExpression.isValidInConstructor(): Boolean {
        val containingClass = getStrictParentOfType<KtClass>() ?: return false
        var isValid = true
        this.accept(object : KtVisitorVoid(), PsiRecursiveVisitor {
            override fun visitKtElement(element: KtElement) {
                element.acceptChildren(this)
            }

            override fun visitReferenceExpression(expression: KtReferenceExpression) {
                val declarationDescriptor = expression.resolveToCall()?.resultingDescriptor ?: return
                if (declarationDescriptor.containingDeclaration == containingClass.descriptor) {
                    isValid = false
                }
            }
        })

        return isValid
    }
}