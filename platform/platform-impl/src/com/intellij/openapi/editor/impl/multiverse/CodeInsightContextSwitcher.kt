// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.multiverse

import com.intellij.codeInsight.multiverse.*
import com.intellij.ide.IdeBundle
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.readAndWriteAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.impl.multiverse.CodeInsightContextSwitcher.SwitcherState.*
import com.intellij.openapi.editor.markup.InspectionWidgetActionProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.impl.ExpandableComboAction
import com.intellij.openapi.wm.impl.ListenableToolbarComboButton
import com.intellij.openapi.wm.impl.ToolbarComboButton
import com.intellij.openapi.wm.impl.ToolbarComboButtonModel
import com.intellij.platform.util.coroutines.childScope
import com.intellij.platform.util.coroutines.sync.OverflowSemaphore
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.time.Duration.Companion.milliseconds

/**
 * Extension providing the context switcher popup that is shown in the top-right corner of an editor.
 * The switcher is shown only if there are at least two contexts available for the current file.
 */
internal class CodeInsightContextSwitcherProvider : InspectionWidgetActionProvider {
  override fun createAction(editor: Editor): AnAction? {
    val project = editor.project?.takeUnless { it.isDefault } ?: return null

    if (!isSharedSourceSupportEnabled(project)) return null

    val file = editor.virtualFile ?: return null

    return CodeInsightContextSwitcher(editor, project, file)
  }
}

/**
 * A project service providing coroutine scopes for switcher machinery
 */
@Service(Service.Level.PROJECT)
private class CodeInsightScopeHolder(val scope: CoroutineScope) {
  companion object {
    fun newChildScope(
      project: Project,
      name: String,
      context: CoroutineContext = EmptyCoroutineContext,
      supervisor: Boolean = true,
    ): CoroutineScope = project.service<CodeInsightScopeHolder>().scope.childScope(name, context, supervisor)
  }
}

/**
 * Expandable Combobox representing the context switcher
 */
private class CodeInsightContextSwitcher(
  private val editor: Editor,
  private val project: Project,
  private val file: VirtualFile,
  private val scope: CoroutineScope = CodeInsightScopeHolder.newChildScope(project, "CodeInsightContextSwitcher's scope"),
) : ExpandableComboAction(), Disposable {

  private val widgetState: MutableStateFlow<SwitcherState> = MutableStateFlow(NotLoaded)
  private val controller = Controller()

  override fun createToolbarComboButton(model: ToolbarComboButtonModel): ToolbarComboButton = SwitcherComboBox(model)

  override fun dispose() {
    scope.cancel()
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  private fun createPopupActionGroup(): DefaultActionGroup {
    val actionGroup = DefaultActionGroup()

    val state = widgetState.value
    val allOptions = state.availableContexts

    if (allOptions != null) {
      for (context in allOptions) {
        val isCurrentlyActiveContext = state.currentContext == context
        val switchContextAction = SwitchContextAction(context, isCurrentlyActiveContext)
        actionGroup.add(switchContextAction)
      }
    }
    else {
      actionGroup.add(DummyLoadingButton())
    }

    return actionGroup
  }

  override fun update(e: AnActionEvent) {
    val state = widgetState.value

    val currentContext = state.currentContext
    val availableContexts = state.availableContexts

    if (currentContext != null && availableContexts != null && availableContexts.size > 1) {
      e.presentation.text = currentContext.text
      e.presentation.icon = currentContext.icon
      e.presentation.isVisible = true
    }
    else {
      // don't show switcher if there are no options to choose (i.e., there is no context at all, or only one context is available)
      e.presentation.isVisible = false
    }
  }

  override fun createPopup(event: AnActionEvent): JBPopup {
    val group = createPopupActionGroup()
    val factory = JBPopupFactory.getInstance()
    return factory.createActionGroupPopup(
      /* title = */ null,
      /* actionGroup = */ group,
      /* dataContext = */ event.dataContext,
      /* aid = */ JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
      /* showDisabledActions = */ true,
      /* disposeCallback = */ null,
      /* maxRowCount = */ -1,
      /* preselectCondition = */ { action -> action is SwitchContextAction && action.isCurrent },
      /* actionPlace = */ null,
    )
  }

  /**
   * An item action to show in the switcher popup
   */
  private inner class SwitchContextAction(
    private val contextPresentation: CodeInsightContextPresentation,
    val isCurrent: Boolean,
  ) : AnAction(contextPresentation.text, null, contextPresentation.icon) {
    override fun actionPerformed(e: AnActionEvent) {
      controller.applyNewActiveContext(contextPresentation)
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
      return ActionUpdateThread.BGT
    }
  }

  /**
   * Dummy button that is used if the contexts are not yet loaded.
   * In general, should never be shown.
   */
  private class DummyLoadingButton : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {}

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
      e.presentation.text = IdeBundle.message("initial.popup.context.switchers.text")
      e.presentation.isEnabled = false
    }
  }

  /**
   * Represents state of code insight context switcher
   * null value means "the value is not loaded yet"
   *
   * Usually, the state order is as follows: [NotLoaded] -> [CurrentContextLoaded] -> [FullStateLoaded]
   *
   * This allows us not to wait for the full list of contexts and apply the current context to UI right away when it's ready.
   */
  private sealed interface SwitcherState {
    val currentContext: CodeInsightContextPresentation?
    val availableContexts: List<CodeInsightContextPresentation>?

    /**
     * An initial empty state
     */
    object NotLoaded : SwitcherState {
      override val currentContext: CodeInsightContextPresentation?
        get() = null
      override val availableContexts: List<CodeInsightContextPresentation>?
        get() = null
    }

    /**
     * Represent an intermediate situation when we know only the current context.
     */
    class CurrentContextLoaded(
      override val currentContext: CodeInsightContextPresentation,
    ) : SwitcherState {
      override val availableContexts: List<CodeInsightContextPresentation>?
        get() = null
    }

    /**
     * Full state
     */
    class FullStateLoaded(
      override val currentContext: CodeInsightContextPresentation,
      override val availableContexts: List<CodeInsightContextPresentation>
    ) : SwitcherState
  }

  /**
   * Controls the switcher UI
   */
  @OptIn(FlowPreview::class)
  private inner class Controller {
    private val updateProcessor = OverflowSemaphore(1, BufferOverflow.DROP_OLDEST)

    init {
      setDefaultContext()

      CodeInsightContextManager.getInstance(project).changeFlow
        .onEach { setDefaultContext() } // todo ijpl-339 how to restore the last used context?
        .launchIn(scope)

      EditorContextManager.getInstance(project).eventFlow
        .filter { it.editor == editor }
        .sample(100.milliseconds)
        .onEach { setContextFromExternalChange(it.newContexts) }
        .launchIn(scope)
    }

    /**
     * applies new active context to the switcher
     */
    fun applyNewActiveContext(presentation: CodeInsightContextPresentation) {
      processUpdate {
        setContext(presentation)
      }
    }

    /**
     * Infers the currently selected context and the list of all available contexts concurrently and applies the current context to UI once
     * it's available without waiting for all contexts.
     */
    private fun setDefaultContext() {
      processUpdate {
        val activePresentationDeferred = async {
          val editorContextManager = EditorContextManager.getInstanceAsync(project)
          val activeContextPresentation = readAction {
            val activeContexts = editorContextManager.getEditorContexts(editor)
            assert(activeContexts is SingleEditorContext) { "multiple contexts are not supported yet" }
            createCodeInsightContextPresentation(activeContexts.mainContext, project)
          }
          activeContextPresentation
        }

        val availableContextsDeferred = async {
          inferAllContexts()
        }

        val activePresentation = activePresentationDeferred.await()
        if (!availableContextsDeferred.isCompleted) {
          // apply active context for UI right away without waiting for all available contexts
          ensureActive()
          widgetState.value = CurrentContextLoaded(activePresentation)
        }

        val availableContexts = availableContextsDeferred.await()
        ensureActive()
        widgetState.value = FullStateLoaded(activePresentation, availableContexts)
      }
    }

    private fun setContextFromExternalChange(newContexts: EditorSelectedContexts) {
      processUpdate {
        assert(newContexts is SingleEditorContext) { "multiple contexts are not supported yet" }
        val newPresentation = createCodeInsightContextPresentation(newContexts.mainContext, project)
        val currentState = updateWidgetState(newPresentation)
        if (currentState !is FullStateLoaded) {
          inferAndApplyAllContexts(newContexts)
        }
      }
    }

    private suspend fun inferAndApplyAllContexts(newContexts: EditorSelectedContexts) {
      val allContexts = inferAllContexts()
      widgetState.updateAndGet { curState ->
        when (curState) {
          is CurrentContextLoaded -> FullStateLoaded(curState.currentContext, allContexts)
          is FullStateLoaded -> FullStateLoaded(curState.currentContext, allContexts)
          NotLoaded -> {
            // todo ijpl-339 should not happend I think?
            val presentation = createCodeInsightContextPresentation(newContexts.mainContext, project)
            FullStateLoaded(presentation, allContexts)
          }
        }
      }
    }

    private fun updateWidgetState(presentation: CodeInsightContextPresentation): SwitcherState {
      return widgetState.updateAndGet { curState ->
        when (curState) {
          is CurrentContextLoaded, NotLoaded -> CurrentContextLoaded(presentation)
          is FullStateLoaded -> FullStateLoaded(presentation, curState.availableContexts)
        }
      }
    }

    private suspend fun setContext(presentation: CodeInsightContextPresentation) = withContext(Dispatchers.Default) {
      updateWidgetState(presentation)

      readAndWriteAction {
        val contextManager = CodeInsightContextManager.getInstance(project)
        val validContexts = contextManager.getCodeInsightContexts(editor.virtualFile)
        val context = presentation.context
        if (context in validContexts) {
          writeAction {
            EditorContextManager.getInstance(project).setEditorContext(editor, SingleEditorContext(context))
          }
        }
        else {
          // todo ijpl-339 report failure?
          value(Unit)
        }
      }
    }

    private suspend fun inferAllContexts(): List<CodeInsightContextPresentation> = withContext(Dispatchers.Default) {
      val contextManager = CodeInsightContextManager.getInstanceAsync(project)
      readAction {
        if (!file.isValid) return@readAction emptyList()
        val contexts = contextManager.getCodeInsightContexts(file)
        contexts.map { createCodeInsightContextPresentation(it, project) }.sortedBy { it.text }
      }
    }

    private fun processUpdate(request: suspend CoroutineScope.() -> Unit) {
      scope.launch {
        updateProcessor.withPermit(request)
      }
    }
  }

  private inner class SwitcherComboBox(model: ToolbarComboButtonModel) : ListenableToolbarComboButton(model) {
    @OptIn(FlowPreview::class)
    override fun installListeners(project: Project?, disposable: Disposable) {
      widgetState
        .debounce(100.milliseconds) // todo ijpl-339 is it fine???
        .onEach { updateWidgetAction() }
        .launchIn(scope.childScope("ui scope", Dispatchers.EDT))
    }

    override fun updateUI() {
      super.updateUI()
      this.foreground = EditorColorsManager.getInstance().globalScheme.defaultForeground
    }
  }
}

