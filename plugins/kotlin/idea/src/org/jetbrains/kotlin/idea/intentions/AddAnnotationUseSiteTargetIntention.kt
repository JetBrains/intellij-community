// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.intentions

import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.ListPopupStep
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.openapi.util.NlsSafe
import com.intellij.psi.PsiComment
import com.intellij.util.PlatformIcons
import org.jetbrains.kotlin.asJava.LightClassUtil
import org.jetbrains.kotlin.descriptors.annotations.AnnotationUseSiteTarget
import org.jetbrains.kotlin.descriptors.annotations.AnnotationUseSiteTarget.*
import org.jetbrains.kotlin.descriptors.annotations.KotlinTarget
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.util.application.executeWriteCommand
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.findDescendantOfType
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.resolve.AnnotationChecker
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode

class AddAnnotationUseSiteTargetIntention : SelfTargetingIntention<KtAnnotationEntry>(
    KtAnnotationEntry::class.java,
    KotlinBundle.lazyMessage("add.use.site.target")
) {

    override fun isApplicableTo(element: KtAnnotationEntry, caretOffset: Int): Boolean {
        val useSiteTargets = element.applicableUseSiteTargets()
        if (useSiteTargets.isEmpty()) return false
        if (useSiteTargets.size == 1) {
            setTextGetter(KotlinBundle.lazyMessage("text.add.use.site.target.0", useSiteTargets.first().renderName))
        } else {
            setTextGetter(KotlinBundle.lazyMessage("add.use.site.target"))
        }
        return true
    }

    override fun applyTo(element: KtAnnotationEntry, editor: Editor?) {
        val useSiteTargets = element.applicableUseSiteTargets()
        element.addUseSiteTarget(useSiteTargets, editor)
    }
}

fun KtAnnotationEntry.addUseSiteTarget(useSiteTargets: List<AnnotationUseSiteTarget>, editor: Editor?) {
    val project = this.project
    if (!isPhysical) {
        // For preview
        if (useSiteTargets.isNotEmpty()) {
            doAddUseSiteTarget(useSiteTargets.first())
        }
        return
    }
    CommandProcessor.getInstance().runUndoTransparentAction {
        if (useSiteTargets.size == 1 || editor == null)
            addUseSiteTarget(useSiteTargets.first(), project)
        else
            JBPopupFactory
                .getInstance()
                .createListPopup(createListPopupStep(this, useSiteTargets, project))
                .showInBestPositionFor(editor)
    }
}

private fun createListPopupStep(
    annotationEntry: KtAnnotationEntry,
    useSiteTargets: List<AnnotationUseSiteTarget>,
    project: Project
): ListPopupStep<*> {
    return object : BaseListPopupStep<AnnotationUseSiteTarget>(KotlinBundle.message("title.choose.use.site.target"), useSiteTargets) {
        override fun isAutoSelectionEnabled() = false

        override fun onChosen(selectedValue: AnnotationUseSiteTarget, finalChoice: Boolean): PopupStep<*>? {
            if (finalChoice) {
                annotationEntry.addUseSiteTarget(selectedValue, project)
            }
            return PopupStep.FINAL_CHOICE
        }

        override fun getIconFor(value: AnnotationUseSiteTarget) = PlatformIcons.ANNOTATION_TYPE_ICON

        override fun getTextFor(value: AnnotationUseSiteTarget): String {
            @Suppress("UnnecessaryVariable")
            @NlsSafe val renderName = value.renderName
            return renderName
        }
    }
}

fun KtAnnotationEntry.applicableUseSiteTargets(): List<AnnotationUseSiteTarget> {
    if (useSiteTarget != null) return emptyList()
    val annotationShortName = this.shortName ?: return emptyList()
    val modifierList = getStrictParentOfType<KtModifierList>() ?: return emptyList()
    val annotated = modifierList.owner as? KtElement ?: return emptyList()

    val candidateTargets = when (annotated) {
        is KtParameter ->
            if (annotated.getStrictParentOfType<KtPrimaryConstructor>() != null)
                when (annotated.valOrVarKeyword?.node?.elementType) {
                    KtTokens.VAR_KEYWORD ->
                        listOf(CONSTRUCTOR_PARAMETER, FIELD, PROPERTY, PROPERTY_GETTER, PROPERTY_SETTER, SETTER_PARAMETER)
                    KtTokens.VAL_KEYWORD ->
                        listOf(CONSTRUCTOR_PARAMETER, FIELD, PROPERTY, PROPERTY_GETTER)
                    else ->
                        emptyList()
                }
            else
                emptyList()
        is KtProperty ->
            when {
                annotated.delegate != null ->
                    listOf(PROPERTY, PROPERTY_GETTER, PROPERTY_DELEGATE_FIELD)
                !annotated.isLocal -> {
                    val backingField = LightClassUtil.getLightClassPropertyMethods(annotated).backingField
                    if (annotated.isVar) {
                        if (backingField != null)
                            listOf(FIELD, PROPERTY, PROPERTY_GETTER, PROPERTY_SETTER, SETTER_PARAMETER)
                        else
                            listOf(PROPERTY, PROPERTY_GETTER, PROPERTY_SETTER, SETTER_PARAMETER)
                    } else {
                        if (backingField != null)
                            listOf(FIELD, PROPERTY, PROPERTY_GETTER)
                        else
                            listOf(PROPERTY, PROPERTY_GETTER)
                    }
                }
                else ->
                    emptyList()
            }
        is KtTypeReference -> listOf(RECEIVER)
        else -> emptyList()
    }.toMutableList()
    if (candidateTargets.isEmpty()) return emptyList()

    val existingTargets = modifierList.annotationEntries.mapNotNull {
        if (annotationShortName == it.shortName) it.useSiteTarget?.getAnnotationUseSiteTarget() else null
    }
    if (existingTargets.isNotEmpty()) {
        candidateTargets.removeIf { it in existingTargets }
        if (candidateTargets.isEmpty()) return emptyList()
    }

    val context = analyze(BodyResolveMode.PARTIAL)
    val descriptor = context[BindingContext.ANNOTATION, this]
    val applicableTargets = descriptor?.let { AnnotationChecker.applicableTargetSet(descriptor) }.orEmpty()
    if (applicableTargets.isNotEmpty()) {
        candidateTargets.removeIf { KotlinTarget.USE_SITE_MAPPING[it] !in applicableTargets }
        if (candidateTargets.isEmpty()) return emptyList()
    }

    return if (isUnitTestMode()) {
        val chosenTarget = containingKtFile.findDescendantOfType<PsiComment>()
            ?.takeIf { it.text.startsWith("// CHOOSE_USE_SITE_TARGET:") }
            ?.text
            ?.split(":")
            ?.getOrNull(1)
            ?.trim()
        if (chosenTarget.isNullOrBlank())
            candidateTargets.take(1)
        else
            candidateTargets.asSequence().filter { it.renderName == chosenTarget }.take(1).toList()
    } else {
        candidateTargets
    }
}

fun KtAnnotationEntry.addUseSiteTarget(useSiteTarget: AnnotationUseSiteTarget, project: Project) {
    project.executeWriteCommand(KotlinBundle.message("add.use.site.target")) {
        doAddUseSiteTarget(useSiteTarget)
    }
}

private fun KtAnnotationEntry.doAddUseSiteTarget(useSiteTarget: AnnotationUseSiteTarget) {
    replace(KtPsiFactory(this).createAnnotationEntry("@${useSiteTarget.renderName}:${text.drop(1)}"))
}
