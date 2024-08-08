// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.base.fir.analysisApiPlatform

import com.intellij.injected.editor.DocumentWindow
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.impl.PsiModificationTrackerImpl
import com.intellij.psi.impl.PsiTreeChangeEventImpl
import com.intellij.psi.impl.PsiTreeChangeEventImpl.PsiEventType
import com.intellij.psi.impl.PsiTreeChangePreprocessor
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtilBase
import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.analysis.api.platform.analysisMessageBus
import org.jetbrains.kotlin.analysis.api.platform.modification.KaElementModificationType
import org.jetbrains.kotlin.analysis.api.platform.modification.KaSourceModificationService
import org.jetbrains.kotlin.analysis.api.platform.modification.KotlinModificationTopics
import org.jetbrains.kotlin.analysis.api.platform.projectStructure.KotlinProjectStructureProvider
import org.jetbrains.kotlin.idea.util.publishGlobalSourceOutOfBlockModification
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.utils.exceptions.errorWithAttachment
import org.jetbrains.kotlin.utils.exceptions.withPsiEntry

internal class FirIdeOutOfBlockPsiTreeChangePreprocessor(private val project: Project) : PsiTreeChangePreprocessor {
    override fun treeChanged(event: PsiTreeChangeEventImpl) {
        if (project.isDefault) {
            return
        }

        if (!PsiModificationTrackerImpl.canAffectPsi(event) ||
            event.isGenericChange ||
            event.code == PsiEventType.BEFORE_CHILD_ADDITION
        ) {
            return
        }

        if (event.isGlobalChange()) {
            // We should not invalidate binary module content here, because global PSI tree changes have no effect on binary modules.
            project.publishGlobalSourceOutOfBlockModification()
            return
        }

        val rootElement = event.parent

        if (rootElement != null) {
            invalidateCachesInInjectedDocuments(rootElement)
        }

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

        val modificationType = when (event.code) {
            PsiEventType.CHILD_ADDED -> KaElementModificationType.ElementAdded
            PsiEventType.CHILD_REMOVED -> {
                val removedElement = event.child ?:
                    errorWithAttachment("A ${PsiEventType.CHILD_REMOVED} PSI tree change event should have a child element") {
                        withEntry("psiTreeChangeEvent", event.toString())
                        withPsiEntry("rootElement", rootElement)
                    }
                KaElementModificationType.ElementRemoved(removedElement)
            }
            else -> KaElementModificationType.Unknown
        }

        KaSourceModificationService.getInstance(project).handleElementModification(child ?: rootElement, modificationType)
    }

    private fun invalidateCachesInInjectedDocuments(rootElement: PsiElement) {
        // check if the change is inside some possibly injected file, e.g., inside a string literal
        val injectionHost = rootElement.parentOfType<PsiLanguageInjectionHost>() ?: return

        @Suppress("DEPRECATION") // there is no other injection API to do this
        val injectedDocuments = InjectedLanguageUtilBase.getCachedInjectedDocuments(rootElement.containingFile)
        if (injectedDocuments.isEmpty()) return

        for (injectedDocument in injectedDocuments) {
            if (rootElement.containsInjection(injectedDocument)) {
                invalidateCachesForInjectedKotlinCode(injectedDocument)
            }
        }
    }

    private fun PsiElement.containsInjection(injectedDocument: DocumentWindow): Boolean {
        return injectedDocument.hostRanges.any { this.textRange.intersects(it) }
    }

    private fun invalidateCachesForInjectedKotlinCode(injectedDocument: DocumentWindow) {
        val ktFile = PsiDocumentManager.getInstance(project).getPsiFile(injectedDocument) as? KtFile ?: return
        val module = KotlinProjectStructureProvider.getModule(project, ktFile, useSiteModule = null)
        project.analysisMessageBus.syncPublisher(KotlinModificationTopics.MODULE_OUT_OF_BLOCK_MODIFICATION).onModification(module)
    }
}

/**
 * The logic for detecting global changes is taken from [PsiModificationTrackerImpl], with the following difference.
 *
 * We don't want to publish any global out-of-block modification on roots changes, because relevant roots changes already cause module
 * state modification events. Such a module state modification event includes the exact module that was affected by the roots change,
 * instead of a less specific global out-of-block modification event. This allows a consumer such as session invalidation to invalidate
 * sessions more granularly. Additionally, many roots changes don't require any event to be published because a corresponding [KaModule][org.jetbrains.kotlin.analysis.api.projectStructure.KaModule]
 * does not exist for the changed module (e.g. when no content roots have been added yet), so roots changes [PsiTreeChangeEvent]s are
 * overzealous, while the module state modification service can handle such cases gracefully.
 */
private fun PsiTreeChangeEventImpl.isGlobalChange() = when (code) {
    PsiEventType.PROPERTY_CHANGED -> propertyName === PsiTreeChangeEvent.PROP_UNLOADED_PSI
    PsiEventType.CHILD_MOVED -> oldParent is PsiDirectory || newParent is PsiDirectory
    else -> parent is PsiDirectory
}
