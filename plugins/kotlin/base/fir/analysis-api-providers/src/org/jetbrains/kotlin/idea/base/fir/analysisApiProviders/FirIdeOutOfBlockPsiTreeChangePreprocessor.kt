// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.base.fir.analysisApiProviders

import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.impl.PsiModificationTrackerImpl
import com.intellij.psi.impl.PsiTreeChangeEventImpl
import com.intellij.psi.impl.PsiTreeChangeEventImpl.PsiEventType
import com.intellij.psi.impl.PsiTreeChangePreprocessor
import org.jetbrains.kotlin.analysis.low.level.api.fir.LLFirInternals
import org.jetbrains.kotlin.analysis.low.level.api.fir.file.structure.getNonLocalReanalyzableContainingDeclaration
import org.jetbrains.kotlin.analysis.low.level.api.fir.file.structure.invalidateAfterInBlockModification
import org.jetbrains.kotlin.analysis.project.structure.KtModule
import org.jetbrains.kotlin.analysis.project.structure.ProjectStructureProvider
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.base.util.module
import org.jetbrains.kotlin.psi.KtAnnotated
import org.jetbrains.kotlin.psi.KtCodeFragment

@OptIn(LLFirInternals::class)
internal class FirIdeOutOfBlockPsiTreeChangePreprocessor(private val project: Project) : PsiTreeChangePreprocessor {
    override fun treeChanged(event: PsiTreeChangeEventImpl) {
        if (!PsiModificationTrackerImpl.canAffectPsi(event) ||
            event.isGenericChange ||
            event.code == PsiEventType.BEFORE_CHILD_ADDITION
        ) {
            return
        }

        if (event.isGlobalChange()) {
            FirIdeOutOfBlockModificationService.getInstance(project).publishGlobalSourceOutOfBlockModification()
            return
        }

        val rootElement = event.parent
        val child = when (event.code) {
            PsiEventType.CHILD_REMOVED -> rootElement
            PsiEventType.BEFORE_CHILD_REPLACEMENT -> event.oldChild
            else -> event.child
        }

        if (rootElement == null || !rootElement.isPhysical) {
            /**
             * Element which do not belong to a project should not cause OOBM
             */
            return
        }

        val changeType = calculateChangeType(event.code, rootElement, child)
        when (changeType) {
            ChangeType.Invisible -> {}
            ChangeType.OutOfBlock -> outOfBlockInvalidation(rootElement)
            is ChangeType.InBlock -> {
                // trigger in-block a FIR tree invalidation
                val isOutOfBlock = !invalidateAfterInBlockModification(changeType.blockOwner)

                // In some cases, we can understand that it is not in-block modification only during in-block modification.
                // Probably it is not reachable, but this statement requires some additional investigation
                if (isOutOfBlock) {
                    outOfBlockInvalidation(rootElement)
                }
            }
        }
    }

    private fun outOfBlockInvalidation(element: PsiElement) {
        if (element.containingFile == null) {
            // PSI elements without a containing file, such as `PsiJavaDirectory`, are not supported by `ProjectStructureProvider`
            // and have no corresponding meaningful `KtModule`.
            element.module?.let { module ->
                FirIdeOutOfBlockModificationService.getInstance(project).publishModuleAndProjectOutOfBlockModification(module)
            }
            return
        }

        val module = ProjectStructureProvider.getModule(project, element, contextualModule = null)
        FirIdeOutOfBlockModificationService.getInstance(project).publishModuleAndProjectOutOfBlockModification(module)
    }

    private fun calculateChangeType(
        code: PsiEventType,
        rootElement: PsiElement,
        child: PsiElement?,
    ): ChangeType = when (rootElement.language) {
        KotlinLanguage.INSTANCE -> kotlinChangeType(code, child ?: rootElement)

        // TODO improve for Java KTIJ-21684
        JavaLanguage.INSTANCE -> ChangeType.OutOfBlock

        // Any other language may cause OOBM in Kotlin too
        else -> ChangeType.OutOfBlock
    }

    private fun kotlinChangeType(code: PsiEventType, psi: PsiElement): ChangeType = when {
        // If PSI is not valid, well something bad happened, OOBM won't hurt
        !psi.isValid -> ChangeType.OutOfBlock
        psi is PsiWhiteSpace || psi is PsiComment -> ChangeType.Invisible
        else -> {
            val inBlockModificationOwner = psi.getNonLocalReanalyzableContainingDeclaration()
                ?: psi.containingFile as? KtCodeFragment

            if (inBlockModificationOwner != null && (psi.parent != inBlockModificationOwner || code != PsiEventType.CHILD_ADDED)) {
                ChangeType.InBlock(inBlockModificationOwner)
            } else {
                ChangeType.OutOfBlock
            }
        }
    }

    /**
     * The logic for detecting global changes is taken from [PsiModificationTrackerImpl], with the following difference.
     *
     * We don't want to publish any global out-of-block modification on roots changes, because relevant roots changes already cause module
     * state modification events. Such a module state modification event includes the exact module that was affected by the roots change,
     * instead of a less specific global out-of-block modification event. This allows a consumer such as session invalidation to invalidate
     * sessions more granularly. Additionally, many roots changes don't require any event to be published because a corresponding [KtModule]
     * does not exist for the changed module (e.g. when no content roots have been added yet), so roots changes [PsiTreeChangeEvent]s are
     * overzealous, while the module state modification service can handle such cases gracefully.
     */
    private fun PsiTreeChangeEventImpl.isGlobalChange() = when (code) {
        PsiEventType.PROPERTY_CHANGED -> propertyName === PsiTreeChangeEvent.PROP_UNLOADED_PSI
        PsiEventType.CHILD_MOVED -> oldParent is PsiDirectory || newParent is PsiDirectory
        else -> parent is PsiDirectory
    }
}

private sealed class ChangeType {
    object OutOfBlock : ChangeType()
    object Invisible : ChangeType()
    class InBlock(val blockOwner: KtAnnotated) : ChangeType()
}
