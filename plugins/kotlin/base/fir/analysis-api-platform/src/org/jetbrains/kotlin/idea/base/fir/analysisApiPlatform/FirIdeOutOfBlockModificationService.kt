// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.base.fir.analysisApiPlatform

import com.intellij.injected.editor.DocumentWindow
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.WriteActionListener
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.registry.Registry
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
import org.jetbrains.kotlin.analysis.api.platform.KotlinAnalysisInWriteActionListener
import org.jetbrains.kotlin.analysis.api.platform.analysisMessageBus
import org.jetbrains.kotlin.analysis.api.platform.modification.KaElementModificationType
import org.jetbrains.kotlin.analysis.api.platform.modification.KaSourceModificationLocality
import org.jetbrains.kotlin.analysis.api.platform.modification.KaSourceModificationService
import org.jetbrains.kotlin.analysis.api.platform.modification.publishGlobalSourceOutOfBlockModificationEvent
import org.jetbrains.kotlin.psi.KtCodeFragment
import org.jetbrains.kotlin.utils.exceptions.errorWithAttachment
import org.jetbrains.kotlin.utils.exceptions.withPsiEntry
import java.util.WeakHashMap

/**
 * Processes PSI tree change events to invalidate caches and publish modification events.
 *
 * To avoid performance problems associated with excessive handling of tree change events, the service memorizes a
 * [TreeChangeProcessingState] that allows avoiding the processing of further events for the duration of the write action.
 */
@ApiStatus.Internal
@Service(Service.Level.PROJECT)
class FirIdeOutOfBlockModificationService(private val project: Project) : Disposable {
    /**
     * The maximum number of files that [FirIdeOutOfBlockModificationService] will process before publishing a global modification event.
     * The processing limit applies for the duration of a single write action.
     *
     * In the case of a multiverse project, an editor document is not necessarily associated with just one [PsiFile]. When multiple
     * [CodeInsightContext][com.intellij.codeInsight.multiverse.CodeInsightContext]s exist for the same virtual file, the service
     * might encounter different [PsiFile]s for the same virtual file in the same write action. Hence, a limit of just one file *does not*
     * ensure that editing a single document always results in a module modification event. With a multiverse context, even a "single-file"
     * edit can affect multiple [PsiFile]s.
     */
    private val FILE_PROCESSING_LIMIT: Int by lazy(LazyThreadSafetyMode.PUBLICATION) {
        Registry.intValue(FILE_PROCESSING_LIMIT_KEY, DEFAULT_FILE_PROCESSING_LIMIT)
    }

    private val threadLocalContext = ThreadLocal.withInitial { TreeChangeHandlingContext() }

    init {
        ApplicationManagerEx.getApplicationEx().addWriteActionListener(ContextRemovalWriteActionListener(), this)

        project.analysisMessageBus
            .connect(this)
            .subscribe(KotlinAnalysisInWriteActionListener.TOPIC, ContextRemovalAnalysisInWriteActionListener())
    }

    private fun handleTreeChangeEvent(event: PsiTreeChangeEventImpl) {
        val context = threadLocalContext.get()
        if (context.state == TreeChangeProcessingState.GlobalEventPublished) {
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
        if (containingFile == null) {
            // When the element doesn't have a containing file, we cannot use it as a key and have to bypass the processing state handling.
            // We still have to process the modification itself since the element could have a meaningful module.
            processElementModification(event, rootElement, containingFile, processingState = null)

            return
        }

        if (containingFile is KtCodeFragment && containingFile.context?.isValid == false) {
            // There is no need to invalidate caches for already invalid code fragments.
            return
        }

        when (val currentState = context.state) {
            is TreeChangeProcessingState.Accepting -> {
                val currentFileState = currentState.fileStates[containingFile]
                when (currentFileState) {
                    null -> {
                        // We've encountered a new file and need to decide if we're still in the bounds of the processing limit.
                        if (currentState.fileCount < FILE_PROCESSING_LIMIT) {
                            currentState.fileStates[containingFile] = TreeChangeFileState.Processing
                            currentState.fileCount += 1

                            processElementModification(event, rootElement, containingFile, currentState)
                        } else {
                            publishGlobalModificationEvent(context)
                        }
                    }

                    TreeChangeFileState.Processing -> {
                        processElementModification(event, rootElement, containingFile, currentState)
                    }

                    // There has already been an OOBM for the file's module, so we can skip the tree change event.
                    TreeChangeFileState.ModuleEventPublished -> {}
                }
            }

            TreeChangeProcessingState.GlobalEventPublished -> {}
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
        containingFile: PsiFile?,
        processingState: TreeChangeProcessingState.Accepting?,
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

        if (containingFile != null && processingState != null && locality is KaSourceModificationLocality.OutOfBlock) {
            processingState.fileStates[containingFile] = TreeChangeFileState.ModuleEventPublished
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

        context.state = TreeChangeProcessingState.GlobalEventPublished
    }

    override fun dispose() {
    }

    /**
     * A stateless listener that delegates to [FirIdeOutOfBlockModificationService].
     *
     * Project extensions don't support [Disposable], so the stateful logic needs to be encapsulated in a project service. Hence, this
     * preprocessor is separate from the modification service itself.
     */
    @ApiStatus.Internal
    class OutOfBlockTreeChangePreprocessor(private val project: Project) : PsiTreeChangePreprocessor {
        override fun treeChanged(event: PsiTreeChangeEventImpl) {
            if (project.isDefault) {
                return
            }

            getInstance(project).handleTreeChangeEvent(event)
        }
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

    /**
     * During a write action, we might already have published OOBMs for several files or even a global OOBM. At that point, we assume that
     * the relevant analysis caches are cleaned, and the tree change preprocessor can rest. However, when `analyze` is called again during
     * the same write action, caches can be filled with new data.
     *
     * To ensure that further modifications can correctly clean caches *again*, we have to reset the state of the tree change preprocessor.
     */
    private inner class ContextRemovalAnalysisInWriteActionListener : KotlinAnalysisInWriteActionListener {
        override fun onEnteringAnalysisInWriteAction() {
            // Resetting the state when *entering* analysis in a write action is not strictly necessary because we don't expect any
            // modifications to happen during the `analyze` call. However, if we do have some modifications during the `analyze` call, we
            // might miss them without the state reset. As it's technically possible, it's better to be safe.
            threadLocalContext.remove()
        }

        override fun afterLeavingAnalysisInWriteAction() {
            threadLocalContext.remove()
        }
    }

    companion object {
        const val FILE_PROCESSING_LIMIT_KEY: String = "kotlin.analysis.treeChangePreprocessor.fileProcessingLimit"

        const val DEFAULT_FILE_PROCESSING_LIMIT: Int = 5

        fun getInstance(project: Project): FirIdeOutOfBlockModificationService =
            project.getService(FirIdeOutOfBlockModificationService::class.java)
    }
}

/**
 * [TreeChangeHandlingContext] memorizes information between tree change events. It lives for the duration of the write action.
 */
private class TreeChangeHandlingContext(
    var state: TreeChangeProcessingState = TreeChangeProcessingState.Accepting(),
)

/**
 * For each [PsiFile], [TreeChangeFileState] tracks the current progress of tree change processing. This allows us to stop tree change
 * processing for the file once an OOBM event has been published.
 *
 * @see FirIdeOutOfBlockModificationService
 */
private sealed class TreeChangeFileState {
    /**
     * The tree change preprocessor is processing tree change events for the file.
     */
    data object Processing : TreeChangeFileState()

    /**
     * The tree change preprocessor has published an out-of-block modification event for the module of the file.
     *
     * If we encounter the same file again, we can skip publishing another module modification event because we've already published such an
     * event.
     *
     * In a multiverse context (see [CodeInsightContext][com.intellij.codeInsight.multiverse.CodeInsightContext]), we cannot assume that
     * different [PsiFile]s with the same underlying virtual file have the same module. One of the core points of Project Multiverse is to
     * allow analysis of the same virtual file in different contexts. Hence, the modules of two multiverse [PsiFile]s might be different
     * even when the underlying virtual file is the same. And so, we need to publish a module modification event for all processed
     * [PsiFile]s of such kind.
     */
    data object ModuleEventPublished : TreeChangeFileState()
}

/**
 * The [TreeChangeProcessingState] tracks whether the tree change preprocessor still accepts tree change events.
 *
 * @see FirIdeOutOfBlockModificationService
 */
private sealed class TreeChangeProcessingState {
    /**
     * The tree change preprocessor is accepting tree change events. It memorizes a [TreeChangeFileState] for each [PsiFile] in [fileStates]
     * to track which files have been encountered and whether an out-of-block modification event has already been published for the file's
     * module.
     *
     * When we encounter files beyond the [processing limit][FirIdeOutOfBlockModificationService.FILE_PROCESSING_LIMIT], we're likely
     * dealing with a complex write action. To avoid excessive processing, the preprocessor should proceed to publish a global modification
     * event and jump straight to [GlobalEventPublished].
     *
     * It's important here to draw the boundary at different files instead of different modules. One of the most expensive operations during
     * tree change processing is getting the module for a specific file. If we instead proceeded to global modification when encountering a
     * different module, we might still process hundreds or thousands of files.
     *
     * The number of processed files is limited regardless of the modification [locality][KaSourceModificationLocality] because in-block or
     * whitespace modification processing can also cause performance problems. As such, all files for which at least one modification has
     * been processed are added to [fileStates].
     */
    class Accepting : TreeChangeProcessingState() {
        /**
         * The number of [PsiFile]s that have been encountered so far.
         *
         * We need a separate counter to track the number of files because [fileStates] has weak keys, which means that entries could be
         * collected as soon as a [PsiFile] becomes unreachable. Hence, [fileStates] is unreliable for counting the exact number of files.
         */
        var fileCount: Int = 0

        /**
         * The [TreeChangeFileState]s memorized for each encountered [PsiFile].
         *
         * The map must have weak references to its [PsiFile] keys to avoid PSI leaks. It does not have to be thread safe because the
         * processing state is held in a thread-local variable.
         */
        val fileStates: MutableMap<PsiFile, TreeChangeFileState> = WeakHashMap()
    }

    /**
     * The tree change preprocessor has published a global modification event.
     *
     * In this context, there is nothing in session invalidation that can be done on top of that, so this state is terminal and effectively
     * turns off the tree change preprocessor for the remainder of the write action.
     */
    data object GlobalEventPublished : TreeChangeProcessingState()
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
