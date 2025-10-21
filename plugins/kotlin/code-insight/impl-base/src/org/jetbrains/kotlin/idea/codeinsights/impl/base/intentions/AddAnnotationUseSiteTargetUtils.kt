// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsights.impl.base.intentions

import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.ListPopupStep
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.openapi.util.NlsSafe
import com.intellij.util.PlatformIcons
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.annotationApplicableTargets
import org.jetbrains.kotlin.analysis.api.components.expandedSymbol
import org.jetbrains.kotlin.analysis.api.components.type
import org.jetbrains.kotlin.asJava.LightClassUtil
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.descriptors.annotations.AnnotationUseSiteTarget
import org.jetbrains.kotlin.descriptors.annotations.AnnotationUseSiteTarget.*
import org.jetbrains.kotlin.descriptors.annotations.KotlinTarget
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsights.impl.base.intentions.AddAnnotationUseSiteTargetUtils.addUseSiteTargetInCommand
import org.jetbrains.kotlin.idea.util.application.executeWriteCommand
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

object AddAnnotationUseSiteTargetUtils {
    context(_: KaSession)
    @OptIn(KaExperimentalApi::class)
    fun KtAnnotationEntry.getApplicableUseSiteTargets(): List<AnnotationUseSiteTarget> {
        val symbol = typeReference?.type?.expandedSymbol
        val applicableTargets = symbol?.annotationApplicableTargets?.toSet().orEmpty()
        return applicableUseSiteTargets(applicableTargets)
    }

    fun KtAnnotationEntry.applicableUseSiteTargets(applicableTargets: Set<KotlinTarget>): List<AnnotationUseSiteTarget> {
        if (useSiteTarget != null) return emptyList()
        val annotationShortName = this.shortName ?: return emptyList()
        val modifierList = getStrictParentOfType<KtModifierList>() ?: return emptyList()
        val annotated = modifierList.owner as? KtElement ?: return emptyList()
        val allIfSupported: AnnotationUseSiteTarget? = ALL.takeIf {
            languageVersionSettings.supportsFeature(LanguageFeature.AnnotationAllUseSiteTarget)
        }

        val candidateTargets = when (annotated) {
            is KtParameter -> if (annotated.getStrictParentOfType<KtPrimaryConstructor>() != null) when (annotated.valOrVarKeyword?.node?.elementType) {
                KtTokens.VAR_KEYWORD -> listOfNotNull(
                    CONSTRUCTOR_PARAMETER, FIELD, PROPERTY, PROPERTY_GETTER, PROPERTY_SETTER, SETTER_PARAMETER, allIfSupported
                )

                KtTokens.VAL_KEYWORD -> listOfNotNull(CONSTRUCTOR_PARAMETER, FIELD, PROPERTY, PROPERTY_GETTER, allIfSupported)

                else -> emptyList()
            }
            else emptyList()

            is KtProperty -> when {
                annotated.delegate != null -> listOf(PROPERTY, PROPERTY_GETTER, PROPERTY_DELEGATE_FIELD)

                !annotated.isLocal -> {
                    val backingField = LightClassUtil.getLightClassPropertyMethods(annotated).backingField
                    if (annotated.isVar) {
                        if (backingField != null) listOfNotNull(FIELD, PROPERTY, PROPERTY_GETTER, PROPERTY_SETTER, SETTER_PARAMETER, allIfSupported)
                        else listOfNotNull(PROPERTY, PROPERTY_GETTER, PROPERTY_SETTER, SETTER_PARAMETER, allIfSupported)
                    } else {
                        if (backingField != null) listOfNotNull(FIELD, PROPERTY, PROPERTY_GETTER, allIfSupported)
                        else listOfNotNull(PROPERTY, PROPERTY_GETTER, allIfSupported)
                    }
                }

                else -> emptyList()
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

        if (applicableTargets.isNotEmpty()) {
            candidateTargets.removeIf {
                KotlinTarget.USE_SITE_MAPPING[it] !in applicableTargets && it != ALL
            }
            if (candidateTargets.isEmpty()) return emptyList()
        }

        return candidateTargets
    }

    /**
     * Add a single explicit annotation use-site target for the annotation entry.
     *
     * The first target (if present) is chosen automatically when there's only one target, the file is non-physical, or there's no editor.
     * Otherwise, a popup dialog will be shown.
     * The PSI update is done in a command action only for physical elements.
     */
    fun KtAnnotationEntry.addOrChooseUseSiteTarget(useSiteTargets: List<AnnotationUseSiteTarget>, editor: Editor?) {
        val project = this.project
        if (!isPhysical) { // For preview
            if (useSiteTargets.isNotEmpty()) {
                addUseSiteTarget(useSiteTargets.first())
            }
            return
        }
        CommandProcessor.getInstance().runUndoTransparentAction {
            if (useSiteTargets.size == 1 || editor == null) addUseSiteTargetInCommand(useSiteTargets.first(), project)
            else JBPopupFactory.getInstance().createListPopup(createListPopupStep(this, useSiteTargets, project))
                .showInBestPositionFor(editor)
        }
    }

    fun KtAnnotationEntry.addUseSiteTargetInCommand(
        useSiteTarget: AnnotationUseSiteTarget, project: Project
    ) {
        project.executeWriteCommand(KotlinBundle.message("add.use.site.target")) {
            addUseSiteTarget(useSiteTarget)
        }
    }

    fun KtAnnotationEntry.addUseSiteTarget(useSiteTarget: AnnotationUseSiteTarget) {
        replace(KtPsiFactory(project).createAnnotationEntry("@${useSiteTarget.renderName}:${text.drop(1)}"))
    }
}

private fun createListPopupStep(
    annotationEntry: KtAnnotationEntry, useSiteTargets: List<AnnotationUseSiteTarget>, project: Project
): ListPopupStep<*> {
    return object : BaseListPopupStep<AnnotationUseSiteTarget>(KotlinBundle.message("title.choose.use.site.target"), useSiteTargets) {
        override fun isAutoSelectionEnabled() = false

        override fun onChosen(selectedValue: AnnotationUseSiteTarget, finalChoice: Boolean): PopupStep<*>? {
            if (finalChoice) {
                annotationEntry.addUseSiteTargetInCommand(selectedValue, project)
            }
            return PopupStep.FINAL_CHOICE
        }

        override fun getIconFor(value: AnnotationUseSiteTarget) = PlatformIcons.ANNOTATION_TYPE_ICON

        override fun getTextFor(value: AnnotationUseSiteTarget): String {
            @NlsSafe val renderName = value.renderName
            return renderName
        }
    }
}
