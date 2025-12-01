// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.base.fir.analysisApiPlatform

import com.intellij.injected.editor.DocumentWindow
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.WriteActionListener
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.psi.PsiTreeChangeEvent
import com.intellij.psi.impl.PsiModificationTrackerImpl
import com.intellij.psi.impl.PsiTreeChangeEventImpl
import com.intellij.psi.impl.PsiTreeChangeEventImpl.PsiEventType
import com.intellij.psi.impl.PsiTreeChangePreprocessor
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtilBase
import com.intellij.psi.util.parentOfType
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.platform.modification.KaElementModificationType
import org.jetbrains.kotlin.analysis.api.platform.modification.KaSourceModificationLocality
import org.jetbrains.kotlin.analysis.api.platform.modification.KaSourceModificationService
import org.jetbrains.kotlin.analysis.api.platform.modification.publishGlobalSourceOutOfBlockModificationEvent
import org.jetbrains.kotlin.psi.KtCodeFragment
import org.jetbrains.kotlin.utils.exceptions.errorWithAttachment
import org.jetbrains.kotlin.utils.exceptions.withPsiEntry

/**
 * Processes PSI tree change events to invalidate caches and publish modification events.
 *
 * To avoid performance problems associated with excessive handling of tree change events, the tree change preprocessor memorizes a
 * [TreeChangeHandlingState] that allows avoiding the processing of further events for the duration of the write action.
 */
@ApiStatus.Internal
class FirIdeOutOfBlockPsiTreeChangePreprocessor(private val project: Project) : PsiTreeChangePreprocessor, Disposable {
    private val threadLocalContext = ThreadLocal.withInitial { TreeChangeHandlingContext() }

    init {
        ApplicationManagerEx.getApplicationEx().addWriteActionListener(ContextRemovalWriteActionListener(), this)
    }

    override fun treeChanged(event: PsiTreeChangeEventImpl) {
        if (project.isDefault) {
            return
        }

        val context = threadLocalContext.get()
        if (context.state == TreeChangeHandlingState.GlobalEventPublished) {
            return
        }

        val hasEventBeenHandled = preprocessEvent(event, context)
        if (hasEventBeenHandled) {
            return
        }

        val rootElement = event.parent ?: return

        val hasEventBeenHandledByRootElement = preprocessRootElement(event, rootElement, context)
        if (hasEventBeenHandledByRootElement) {
            return
        }

        val containingFile = rootElement.containingFile

        if (containingFile is KtCodeFragment && containingFile.context?.isValid == false) {
            // There is no need to invalidate caches for already invalid code fragments.
            return
        }

        // We have to handle the rest of the states here because preceding lines may want to publish a global modification event even for
        // the same file.
        when (val currentState = context.state) {
            TreeChangeHandlingState.Accepting -> {
                // We've encountered the first legitimate event. The tree change preprocessor will be limited to its file for the remainder
                // of the write action.
                context.state = TreeChangeHandlingState.ProcessingFile(containingFile)

                processElementModification(event, rootElement, containingFile, context)
            }

            is TreeChangeHandlingState.ProcessingFile -> {
                if (currentState.file == containingFile) {
                    processElementModification(event, rootElement, containingFile, context)
                } else {
                    publishGlobalModificationEvent(context)
                }
            }

            is TreeChangeHandlingState.ModuleEventPublished -> {
                // If the processed file and containing file match, we do nothing: There has already been an OOBM so we can skip the event.
                if (currentState.processedFile != containingFile) {
                    publishGlobalModificationEvent(context)
                }
            }

            TreeChangeHandlingState.GlobalEventPublished -> {}
        }
    }

    /**
     * @return Whether the given event has been handled.
     */
    private fun preprocessEvent(event: PsiTreeChangeEventImpl, context: TreeChangeHandlingContext): Boolean {
        val eventCode = event.code
        if (!PsiModificationTrackerImpl.canAffectPsi(event) ||
            event.isGenericChange ||
            eventCode == PsiEventType.BEFORE_CHILD_ADDITION
        ) {
            return true
        }

        if (event.isGlobalChange()) {
            publishGlobalModificationEvent(context)
            return true
        }

        return false
    }

    /**
     * @return Whether the given event has been handled by processing the root element.
     */
    private fun preprocessRootElement(
        event: PsiTreeChangeEventImpl,
        rootElement: PsiElement,
        context: TreeChangeHandlingContext
    ): Boolean {
        val eventCode = event.code

        // No reasons to invalidate injected documents before an actual replacement.
        if (eventCode != PsiEventType.BEFORE_CHILD_REPLACEMENT) {
            if (isInjectionChange(rootElement)) {
                // Finding the exact `KtFile` for the injection is expensive and can lead to freezes (see KTIJ-36275). A global modification
                // event can be published without a `KtFile`/`KaModule`. Since changes in injected files should be rare, such an event
                // should be fine performance-wise.
                publishGlobalModificationEvent(context)
                return true
            }
        }

        if (!rootElement.isPhysical) {
            // Elements which do not belong to the project should not cause an OOBM.
            return true
        }

        return false
    }

    private fun processElementModification(
        event: PsiTreeChangeEventImpl,
        rootElement: PsiElement,
        containingFile: PsiFile,
        context: TreeChangeHandlingContext,
    ) {
        val modificationType = when (event.code) {
            PsiEventType.CHILD_ADDED -> KaElementModificationType.ElementAdded
            PsiEventType.CHILD_REMOVED -> {
                val removedElement = event.child
                    ?: errorWithAttachment("A ${PsiEventType.CHILD_REMOVED} PSI tree change event should have a child element") {
                        withEntry("psiTreeChangeEvent", event.toString())
                        withPsiEntry("rootElement", rootElement)
                    }
                KaElementModificationType.ElementRemoved(removedElement)
            }

            else -> KaElementModificationType.Unknown
        }

        val child = when (event.code) {
            PsiEventType.CHILD_REMOVED -> rootElement
            PsiEventType.BEFORE_CHILD_REPLACEMENT -> event.oldChild
            else -> event.child
        }
        val targetElement = child ?: rootElement

        val sourceModificationService = KaSourceModificationService.getInstance(project)
        val locality = sourceModificationService.detectLocality(targetElement, modificationType)

        sourceModificationService.handleInvalidation(targetElement, locality)

        if (locality is KaSourceModificationLocality.OutOfBlock) {
            context.state = TreeChangeHandlingState.ModuleEventPublished(containingFile)
        }
    }

    private fun isInjectionChange(rootElement: PsiElement): Boolean {
        // check if the change is inside some possibly injected file, e.g., inside a string literal
        val injectionHost = rootElement.parentOfType<PsiLanguageInjectionHost>()
        if (injectionHost == null) {
            return false
        }

        @Suppress("DEPRECATION") // there is no other injection API to do this
        val injectedDocuments = InjectedLanguageUtilBase.getCachedInjectedDocuments(rootElement.containingFile)
        if (injectedDocuments.isEmpty()) return false

        // Compute the text range once since it's not necessarily cached and can cause performance issues if repeatedly accessed (see
        // KTIJ-36275).
        val textRange = rootElement.textRange

        return injectedDocuments.any { it.containsInjectionAt(textRange) }
    }

    private fun DocumentWindow.containsInjectionAt(textRange: TextRange): Boolean =
        this.hostRanges.any { textRange.intersects(it) }

    private fun publishGlobalModificationEvent(context: TreeChangeHandlingContext) {
        // We should only invalidate source module content here because global PSI tree changes have no effect on binary modules.
        project.publishGlobalSourceOutOfBlockModificationEvent()

        context.state = TreeChangeHandlingState.GlobalEventPublished
    }

    override fun dispose() {
    }

    private inner class ContextRemovalWriteActionListener : WriteActionListener {
        override fun writeActionStarted(action: Class<*>) {
            // Cleaning up on write action start is not strictly necessary because the context should have been cleaned up at the end of
            // the previous write action. But it's good to ensure that we start a write action with a clean context in case something went
            // wrong.
            threadLocalContext.remove()
        }

        override fun writeActionFinished(action: Class<*>) {
            threadLocalContext.remove()
        }
    }
}

/**
 * [TreeChangeHandlingContext] memorizes information between tree change events. It lives for the duration of the write action.
 */
private class TreeChangeHandlingContext(
    var state: TreeChangeHandlingState = TreeChangeHandlingState.Accepting,
)

/**
 * The tree change preprocessor is a state machine that can be in four different states: [Accepting], [ProcessingFile],
 * [ModuleEventPublished], and [GlobalEventPublished].
 *
 * @see FirIdeOutOfBlockPsiTreeChangePreprocessor
 */
private sealed class TreeChangeHandlingState {
    /**
     * The tree change preprocessor has not yet published modification events and is accepting tree change events from any files.
     */
    data object Accepting : TreeChangeHandlingState()

    /**
     * The tree change preprocessor is processing tree change events only for [file].
     *
     * If we encounter a different file, we're likely dealing with a complex write action. To avoid excessive processing, we proceed to
     * publish a global modification event (and jump straight to [GlobalEventPublished]).
     *
     * It's important here to draw the boundary at different files instead of different modules. One of the most expensive operations during
     * tree change processing is getting the module for a specific file. If we instead proceeded to global modification when encountering a
     * different module, we might still process hundreds or thousands of files.
     *
     * Note that this state is necessary to catch multi-file processing when no out-of-block modification has been detected yet. For
     * example, we could have in-block modification in multiple files. Because in-block modification processing can also cause performance
     * problems, we should still proceed with a global modification event.
     */
    data class ProcessingFile(val file: PsiFile) : TreeChangeHandlingState()

    /**
     * The tree change preprocessor has published an out-of-block modification event for the module of [processedFile].
     *
     * If we encounter the same file again, we can skip publishing another module modification event because we've already published such an
     * event. If it's a different file, we publish a global modification event like in [ProcessingFile].
     */
    data class ModuleEventPublished(val processedFile: PsiFile) : TreeChangeHandlingState()

    /**
     * The tree change preprocessor has published a global modification event.
     *
     * In this context, there is nothing in session invalidation that can be done on top of that, so this state is terminal and effectively
     * turns off the tree change preprocessor for the remainder of the write action.
     */
    data object GlobalEventPublished : TreeChangeHandlingState()
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
