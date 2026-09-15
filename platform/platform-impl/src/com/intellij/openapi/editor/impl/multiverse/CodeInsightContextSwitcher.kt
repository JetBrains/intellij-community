// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.multiverse

import com.intellij.codeInsight.multiverse.CodeInsightContextManager
import com.intellij.codeInsight.multiverse.EditorContextManager
import com.intellij.codeInsight.multiverse.EditorSelectedContexts
import com.intellij.codeInsight.multiverse.SingleEditorContext
import com.intellij.codeInsight.multiverse.isSharedSourceSupportEnabled
import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.readAndEdtWriteAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.impl.multiverse.CodeInsightContextSwitcher.SwitcherState.CurrentContextLoaded
import com.intellij.openapi.editor.impl.multiverse.CodeInsightContextSwitcher.SwitcherState.FullStateLoaded
import com.intellij.openapi.editor.impl.multiverse.CodeInsightContextSwitcher.SwitcherState.NotLoaded
import com.intellij.openapi.editor.markup.InspectionWidgetActionProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.impl.ExpandableComboAction
import com.intellij.openapi.wm.impl.ListenableToolbarComboButton
import com.intellij.openapi.wm.impl.ToolbarComboButton
import com.intellij.openapi.wm.impl.ToolbarComboButtonModel
import com.intellij.platform.util.coroutines.childScope
import com.intellij.platform.util.coroutines.sync.OverflowSemaphore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

    return CodeInsightContextSwitcher(editor, project, file).widgetGroup
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
internal class CodeInsightContextSwitcher(
  private val editor: Editor,
  private val project: Project,
  private val file: VirtualFile,
  private val scope: CoroutineScope = CodeInsightScopeHolder.newChildScope(project, "CodeInsightContextSwitcher's scope"),
) : ExpandableComboAction(), Disposable {

  private val widgetState: MutableStateFlow<SwitcherState> = MutableStateFlow(NotLoaded)
  private val controller = Controller()

  /** The reset button and the combo. Rendered inline, so the reset sits left of the combo; disposing it disposes this. */
  val widgetGroup: DefaultActionGroup = WidgetGroup()

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
    val shownContext = shownContext()
    e.presentation.isVisible = shownContext != null
    if (shownContext != null) {
      e.presentation.text = shownContext.text
      e.presentation.icon = shownContext.icon
      // AbstractToolbarCombo.updateFromPresentation renders the description as the combo's tooltip.
      e.presentation.description = shownContext.tooltip
    }
  }

  /** The context to show, or `null` to hide: with fewer than two contexts there is nothing to choose between. */
  private fun shownContext(): CodeInsightContextPresentation? {
    val state = widgetState.value
    return state.currentContext?.takeIf { (state.availableContexts?.size ?: 0) > 1 }
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

    override fun update(e: AnActionEvent) {
      // A popup list item takes its tooltip from TOOLTIP_TEXT (PopupFactoryImpl.ActionItem), not from the description.
      e.presentation.putClientProperty(ActionUtil.TOOLTIP_TEXT, contextPresentation.tooltip)
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
      return ActionUpdateThread.BGT
    }
  }

  /**
   * Clears whatever override the owning provider has on the file, for example a per-file pin. An `x` button next to the
   * combo rather than an entry in the popup, because it is not one of the contexts to choose between.
   */
  private inner class ResetContextAction : AnAction(AllIcons.Actions.Close) {
    init {
      templatePresentation.hoveredIcon = AllIcons.Actions.CloseHovered
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
      // Tied to the combo on purpose: with nothing to switch between there is nothing to reset back to either.
      // No read action needed, a BGT update already holds the read lock.
      val reset = if (shownContext() == null) null
      else EditorContextManager.getInstance(project).getContextResetAction(editor)
      e.presentation.isVisible = reset != null
      // Icon-only button, so the text is the tooltip rather than a label.
      e.presentation.text = if (reset == null) "" else IdeBundle.message("context.switcher.reset.text")
      e.presentation.description = e.presentation.text
    }

    override fun actionPerformed(e: AnActionEvent) {
      // Deliberately not processUpdate: its semaphore drops queued work, which is fine for a recompute but not a click.
      scope.launch {
        val reset = readAction { EditorContextManager.getInstance(project).getContextResetAction(editor) } ?: return@launch
        reset()
      }
    }
  }

  private inner class WidgetGroup : DefaultActionGroup(ResetContextAction(), this@CodeInsightContextSwitcher), Disposable {
    override fun dispose() {
      Disposer.dispose(this@CodeInsightContextSwitcher)
    }
  }

  /**
   * Dummy button that is used if the contexts are not yet loaded.
   * In general, should never be shown.
   */
  internal class DummyLoadingButton : AnAction() {
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
        .onEach { setDefaultContext() } // todo IJPL-339 how to restore the last used context?
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
            // todo IJPL-339 should not happend I think?
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

      val contextManager = CodeInsightContextManager.getInstance(project)
      val editorContextManager = EditorContextManager.getInstance(project)
      val virtualFile = editor.virtualFile!!
      val context = presentation.context

      val applied = readAndEdtWriteAction {
        if (context in contextManager.getCodeInsightContexts(virtualFile)) {
          writeAction {
            editorContextManager.setEditorContext(editor, SingleEditorContext(context))
            true
          }
        }
        else {
          // todo IJPL-339 report failure?
          value(false)
        }
      }

      // After the pick is applied, and outside the read-write block, so a retried block cannot report it twice.
      if (applied) {
        readAction { editorContextManager.notifyContextPicked(editor, context) }
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
        .debounce(100.milliseconds) // todo IJPL-339 is it fine???
        .onEach { updateWidgetAction() }
        .launchIn(scope.childScope("ui scope", Dispatchers.EDT))
    }

    override fun updateUI() {
      super.updateUI()
      this.foreground = EditorColorsManager.getInstance().globalScheme.defaultForeground
    }
  }
}

