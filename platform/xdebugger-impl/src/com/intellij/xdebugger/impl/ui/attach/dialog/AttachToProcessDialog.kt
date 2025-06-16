// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.ui.attach.dialog

import com.intellij.icons.AllIcons
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ComboBoxAction
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.observable.properties.AtomicLazyProperty
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.OptionAction
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.JBColor
import com.intellij.ui.SearchTextField
import com.intellij.ui.components.JBOptionButton
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.RightGap
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.application
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.Update
import com.intellij.xdebugger.XDebuggerBundle
import com.intellij.xdebugger.attach.XAttachDebugger
import com.intellij.xdebugger.attach.XAttachDebuggerProvider
import com.intellij.xdebugger.attach.XAttachHost
import com.intellij.xdebugger.attach.XAttachHostProvider
import com.intellij.xdebugger.impl.actions.AttachToProcessActionBase.AttachToProcessItem
import com.intellij.xdebugger.impl.ui.attach.dialog.extensions.XAttachDialogUiInvisibleDebuggerProvider
import com.intellij.xdebugger.impl.ui.attach.dialog.extensions.XAttachToProcessViewProvider
import com.intellij.xdebugger.impl.ui.attach.dialog.extensions.getActionPresentation
import com.intellij.xdebugger.impl.ui.attach.dialog.items.AttachToProcessItemsListBase
import com.intellij.xdebugger.impl.ui.attach.dialog.items.columns.AttachDialogColumnsLayoutService
import com.intellij.xdebugger.impl.ui.attach.dialog.statistics.AttachDialogStatisticsCollector
import net.miginfocom.swing.MigLayout
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.awt.event.*
import javax.swing.*
import javax.swing.event.DocumentEvent

@ApiStatus.Internal
open class AttachToProcessDialog(
  private val project: Project,
  private val attachDebuggerProviders: List<XAttachDebuggerProvider>,
  private val attachHostProviders: List<XAttachHostProvider<XAttachHost>>,
  dataContext: DataContext = DataContext.EMPTY_CONTEXT,
  defaultHostType: AttachDialogHostType = AttachDialogHostType.LOCAL,
  parentComponent: JComponent? = null) : DialogWrapper(project, parentComponent, true, IdeModalityType.MODELESS) {

  companion object {
    internal const val ATTACH_DIALOG_SELECTED_DEBUGGER = "ATTACH_DIALOG_SELECTED_DEBUGGER"

    private val logger = Logger.getInstance(AttachToProcessDialog::class.java)
  }

  protected val filterTextField: SearchTextField = SearchTextField(false).apply {
    border = JBUI.Borders.customLine(JBColor.border(), 0, 0, 1, 0)

    val editor: JBTextField = textEditor
    editor.border = JBUI.Borders.empty()
    editor.isOpaque = true

    textEditor.addKeyListener(object : KeyListener {
      override fun keyTyped(e: KeyEvent?) {
      }

      override fun keyPressed(e: KeyEvent?) {
        if (e?.keyCode != KeyEvent.VK_DOWN) {
          return
        }

        textEditor.transferFocus()
        state.currentList.get()?.selectNextItem()
      }

      override fun keyReleased(e: KeyEvent?) {
      }
    })
  }

  private val state = AttachDialogState(disposable, dataContext)

  private val filterTypingMergeQueue: MergingUpdateQueue = MergingUpdateQueue(
    "Attach to process search typing merging queue",
    200,
    true,
    null,
    disposable,
    null,
    false
  ).setRestartTimerOnAdd(true)

  private var filteringPattern: String = ""

  private val columnsLayout = application.getService(AttachDialogColumnsLayoutService::class.java).getColumnsLayout()

  private val localAttachView = AttachToLocalProcessView(project, state, columnsLayout, attachDebuggerProviders)
  private val externalAttachViews: List<AttachToProcessView>
  private val allViews: List<AttachToProcessView>
  private var currentAttachView = AtomicLazyProperty<AttachToProcessView> { localAttachView }

  private val viewsPanel: DialogPanel

  private val viewPanel = JPanel(MigLayout("ins 0, fill, gap 0, novisualpadding")).apply {
    minimumSize = Dimension(columnsLayout.getMinimumViewWidth(), JBUI.scale(400))
    border = JBUI.Borders.customLine(JBColor.border(), 1, 1, 1, 1)
  }

  private val northToolbar: ActionToolbar

  init {
    title = XDebuggerBundle.message("xdebugger.attach.action").trimEnd('.')

    externalAttachViews = XAttachToProcessViewProvider
      .getProcessViews(project, state, columnsLayout, attachDebuggerProviders, attachHostProviders)
    allViews = listOf(localAttachView) + externalAttachViews
    viewsPanel = panel { row { segmentedButton(allViews) { text = it.getName() }.bind(currentAttachView) } }

    northToolbar = createNorthToolbar()
    viewPanel.add(filterTextField, "wrap, grow")
    updateProblemStripe()
    currentAttachView.afterChange { updateView(it) }
    val view = when (defaultHostType) {
      AttachDialogHostType.LOCAL -> localAttachView
      else -> externalAttachViews.firstOrNull { it.getHostType() == defaultHostType } ?: localAttachView
    }

    currentAttachView.set(view)

    currentAttachView.afterChange { AttachDialogStatisticsCollector.hostSwitched(it) } // register view switch logging only after initialization

    filterTextField.addDocumentListener(object : DocumentAdapter() {
      override fun insertUpdate(e: DocumentEvent) {
        super.insertUpdate(e)
        onSearchFieldInsertUpdate()
      }

      override fun textChanged(e: DocumentEvent) {
        if (filteringPattern == filterTextField.text) return

        filteringPattern = filterTextField.text
        filterTypingMergeQueue.queue(object : Update(filterTextField) {
          override fun run() {
            if (filteringPattern == state.searchFieldValue.get()) {
              return
            }
            state.searchFieldValue.set(filteringPattern)
          }
        })
      }
    })

    installFocusTraversePolicy()

    state.currentList.afterChange { onNextListAvailable(it) }
    state.itemWasDoubleClicked.afterChange { if (it) onItemDoubleClicked() }
    state.selectedViewType.afterChange { updateProcesses() }

    val attachAction = getAttachAction()
    state.selectedDebuggerItem.afterChange {
      updateProblemStripe(if (it != null && it.getGroups().isEmpty()) XDebuggerBundle.message("xdebugger.attach.dialog.no.debuggers.is.available.message") else null)
      attachAction.setItem(it)
      getOkOptionButton()?.options = attachAction.options
    }
    state.selectedDebuggersFilter.afterChange {
      attachAction.onFilterUpdated()
      getOkOptionButton()?.options = attachAction.options
    }
    okAction.isEnabled = false

    super.init()
  }

  override fun getDimensionServiceKey(): String? = AttachToProcessDialog::class.java.simpleName

  override fun createCenterPanel(): JComponent {
    return viewPanel
  }

  override fun createNorthPanel(): JComponent = northToolbar.component

  override fun getPreferredFocusedComponent(): JComponent = filterTextField.textEditor

  protected open fun attach(debugger: XAttachDebugger, item: AttachToProcessItem) {
    attachToProcessWithDebugger(debugger, item, project)
    super.doOKAction()
  }

  override fun createDefaultActions() {
    super.createDefaultActions()
    myOKAction = AttachAction()
  }

  protected open fun onSearchFieldInsertUpdate() {
    if (filterTextField.text?.length == 1) {
      AttachDialogStatisticsCollector.searchFieldUsed()
    }
  }

  private fun onItemDoubleClicked() {
    application.invokeLater {
      okAction.actionPerformed(ActionEvent(this, MouseEvent.MOUSE_CLICKED, "double-click"))
    }
  }

  private fun updateProblemStripe(@Nls text: String? = null) {
    if (text == null) {
      setErrorInfoAll(emptyList())
      return
    }
    setErrorInfoAll(listOf(ValidationInfo(text).asWarning()))
  }

  private fun onNextListAvailable(list: AttachToProcessItemsListBase?) {
    filterTextField.requestFocus()
    if (list != null) {
      installKeyListener(list)
    }
  }

  private fun installKeyListener(list: AttachToProcessItemsListBase) {

    fun isNonPrintable(code: Int): Boolean{
      return code < 32 || code in 128..159
    }

    val focusInputMap = list.getFocusedComponent().getInputMap(JComponent.WHEN_FOCUSED)
    val shortcut = KeymapManager.getInstance().activeKeymap
      .getShortcuts("\$SelectAll").filterIsInstance<KeyboardShortcut>().firstOrNull()?.firstKeyStroke
                   ?: KeyStroke.getKeyStroke(KeyEvent.VK_A, InputEvent.CTRL_DOWN_MASK)

    focusInputMap.put(shortcut, "selectAll")
    list.getFocusedComponent().actionMap.put("selectAll", object : AbstractAction() {
      override fun actionPerformed(e: ActionEvent?) {
        filterTextField.selectText()
      }
    })

    val keyListener = object : KeyAdapter() {
      override fun keyPressed(e: KeyEvent?) {
        if (e == null) return
        if (e.isActionKey) return
        if (e.isControlDown && e.keyChar == 'a') {
          filterTextField.selectText()
          return
        }
        if (e.keyCode == KeyEvent.VK_BACK_SPACE && filterTextField.text.any()) {
          if (filterTextField.textEditor.selectedText != null) {
            filterTextField.textEditor.replaceSelection("")
            return
          }
          filterTextField.text = filterTextField.text.substring(0, filterTextField.text.length - 1)
          return
        }
        if (isNonPrintable(e.keyCode)) return
        filterTextField.text += e.keyChar
      }
    }
    list.getFocusedComponent().addKeyListener(keyListener)
  }

  fun updateProcesses() {
    currentAttachView.get().updateProcesses()
  }

  private fun getAttachAction(): AttachAction = okAction as AttachAction
  private fun getOkOptionButton(): JBOptionButton? {
    val button = getButton(okAction)
    if (button == null) {
      logger.error("Ok button is not available")
      return null
    }
    if (button !is JBOptionButton) {
      logger.warn("Attach dialog OK button is not an ${JBOptionButton::class.java.simpleName} (Actual button type is ${button::class.java}). Do you have 'ide.allow.merge.buttons' registry value disabled?")
      return null
    }

    return button
  }

  private fun updateView(view: AttachToProcessView) {
    if (viewPanel.components.size > 1) {
      viewPanel.remove(viewPanel.components.size - 1)
    }

    viewPanel.add(view.getMainComponent(), "push, grow, wrap")
    updateProcesses()
    viewPanel.revalidate()
    viewPanel.repaint()
    northToolbar.component.revalidate()
    northToolbar.component.repaint()
  }

  private fun createNorthToolbar(): ActionToolbar {
    val actions = mutableListOf<AnAction>()

    if (attachHostProviders.isNotEmpty()) {
      actions.add(SelectedHostAction())
    }

    for (view in allViews) {
      actions.addAll(view.getViewActions().map { createWrapper(view, it) })
    }

    actions.add(RefreshActionButton())
    actions.add(SelectedViewAction())
    actions.add(DebuggerFilterComboBox())
    actions.add(ActionManager.getInstance().getAction("XDebugger.Attach.Dialog.Settings"))

    return ActionManager.getInstance().createActionToolbar(ActionPlaces.ATTACH_DIALOG_TOOLBAR, DefaultActionGroup(actions), true)
  }

  private fun installFocusTraversePolicy() {
    val rootPane = rootPane ?: return
    rootPane.isFocusCycleRoot = true
    rootPane.isFocusTraversalPolicyProvider = true
    rootPane.focusTraversalPolicy = object : LayoutFocusTraversalPolicy() {
      override fun getComponentAfter(aContainer: Container?, aComponent: Component?): Component? {
        aComponent ?: return super.getComponentAfter(aContainer, null)

        val components = getOrderedComponents()
        val indexOfComponent = components.indexOf(aComponent)

        if (indexOfComponent < 0) {
          return super.getComponentAfter(aContainer, aComponent)
        }

        for (i in indexOfComponent + 1 until components.size) {
          val component = components[i]
          if (component != null && component.isEnabled) {
            return component
          }
        }

        for (i in 0 until indexOfComponent) {
          val component = components[i]
          if (component != null && component.isEnabled) {
            return component
          }
        }

        return null
      }

      override fun getComponentBefore(aContainer: Container?, aComponent: Component?): Component? {
        aComponent ?: return super.getComponentBefore(aContainer, null)

        val components = getOrderedComponents()
        val indexOfComponent = components.indexOf(aComponent)

        if (indexOfComponent < 0) {
          return super.getComponentBefore(aContainer, aComponent)
        }

        for (i in indexOfComponent - 1 downTo 0) {
          val component = components[i]
          if (component != null && component.isEnabled) {
            return component
          }
        }

        for (i in components.size - 1 downTo indexOfComponent + 1) {
          val component = components[i]
          if (component != null && component.isEnabled) {
            return component
          }
        }

        return null
      }

      private fun getOrderedComponents() =
        listOf(
          filterTextField.textEditor,
          currentAttachView.get().getFocusedComponent(),
          getButton(okAction),
          getButton(cancelAction),
          viewsPanel.components.firstOrNull())
    }
  }

  private inner class RefreshActionButton : DumbAwareAction("", null, AllIcons.Actions.Refresh), DumbAware {
    override fun actionPerformed(e: AnActionEvent) {
      updateProcesses()
    }
  }

  private inner class DebuggerFilterComboBox : ComboBoxAction(), RightAlignedToolbarAction, DumbAware {

    private val allAvailableDebuggersFilters = listOf(AttachDialogAllDebuggersFilter) + attachDebuggerProviders
      .asSequence()
      .filter { it !is XAttachDialogUiInvisibleDebuggerProvider }
      .map { it.presentationGroup }
      .distinct()
      .sortedBy { it.order }.map { AttachDialogDebuggersFilterByGroup(it) }
      .toList()

    init {
      isSmallVariant = false
      state.selectedDebuggersFilter.set(getDefaultDebuggersFilter())
      state.selectedDebuggersFilter.afterChange {
        PropertiesComponent.getInstance().setValue(ATTACH_DIALOG_SELECTED_DEBUGGER, it.getPersistentKey())
      }
    }

    override fun createPopupActionGroup(button: JComponent, context: DataContext): DefaultActionGroup {

      val actions = DefaultActionGroup()

      for (debuggersFilter in allAvailableDebuggersFilters) {
        actions.add(object : AnAction({ debuggersFilter.getDisplayText() }) {
          override fun actionPerformed(e: AnActionEvent) {
            state.selectedDebuggersFilter.set(debuggersFilter)
            if (debuggersFilter != AttachDialogAllDebuggersFilter) {
              AttachDialogStatisticsCollector.debuggersFilterSet()
            }
          }
        })
      }

      return actions
    }

    override fun update(e: AnActionEvent) {
      e.presentation.isEnabledAndVisible = true
      e.presentation.text = state.selectedDebuggersFilter.get().getDisplayText()
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    private fun getDefaultDebuggersFilter(): AttachDialogDebuggersFilter {
      val savedValue = PropertiesComponent.getInstance().getValue(ATTACH_DIALOG_SELECTED_DEBUGGER)
                       ?: return AttachDialogAllDebuggersFilter
      return allAvailableDebuggersFilters.firstOrNull { it.getPersistentKey() == savedValue } ?: AttachDialogAllDebuggersFilter
    }
  }

  private inner class SelectedViewAction : AnAction(), CustomComponentAction, RightAlignedToolbarAction, DumbAware {
    override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
      return panel {
        row {
          label(XDebuggerBundle.message("xdebugger.attach.view.message")).gap(RightGap.SMALL)
          segmentedButton(AttachViewType.values().toList()) { text = it.displayText }.bind(state.selectedViewType)
        }
      }
    }

    override fun actionPerformed(e: AnActionEvent) {
    }
  }

  private inner class SelectedHostAction : AnAction(), CustomComponentAction, DumbAware {
    override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
      return viewsPanel
    }

    override fun actionPerformed(e: AnActionEvent) {
    }

  }

  private inner class AttachViewActionWrapper(private val attachView: AttachToProcessView, private val action: AnAction) :
    AnAction(action.templatePresentation.text, action.templatePresentation.description, action.templatePresentation.icon), DumbAware {
    override fun actionPerformed(e: AnActionEvent) {
      action.actionPerformed(e)
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun update(e: AnActionEvent) {
      if (currentAttachView.get() != attachView) {
        e.presentation.isEnabledAndVisible = false
        return
      }
      action.update(e)
    }
  }

  private inner class AttachViewCustomComponentActionWrapper(
    private val attachView: AttachToProcessView,
    private val action: CustomComponentAction) :
    CustomComponentAction, AnAction(
    (action as? AnAction)?.templatePresentation?.text,
    (action as? AnAction)?.templatePresentation?.description,
    (action as? AnAction)?.templatePresentation?.icon), DumbAware {

    override fun actionPerformed(e: AnActionEvent) {
      (action as? AnAction)?.actionPerformed(e)
    }

    override fun update(e: AnActionEvent) {
      if (currentAttachView.get() != attachView) {
        e.presentation.isEnabledAndVisible = false
        return
      }
      (action as? AnAction)?.update(e)
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
      return action.createCustomComponent(presentation, place)
    }

    override fun updateCustomComponent(component: JComponent, presentation: Presentation) {
      action.updateCustomComponent(component, presentation)
    }
  }

  private fun createWrapper(view: AttachToProcessView, action: AnAction): AnAction {
    if (action is CustomComponentAction) return AttachViewCustomComponentActionWrapper(view, action)
    return AttachViewActionWrapper(view, action)
  }

  fun setAttachView(hostType: AttachDialogHostType) {
    val viewToSelect = when (hostType) {
      AttachDialogHostType.LOCAL -> localAttachView
      else -> externalAttachViews.firstOrNull { it.getHostType() == hostType } ?: localAttachView
    }
    if (currentAttachView.get() == viewToSelect) {
      return
    }
    currentAttachView.set(viewToSelect)
  }

  private inner class AttachAction : AbstractAction(), OptionAction, DumbAware {

    init {
      putValue(DEFAULT_ACTION, java.lang.Boolean.TRUE)
    }

    private var debuggers: List<AttachDebuggerAction> = emptyList()
    private var selectedItem: AttachDialogProcessItem? = null

    fun setItem(item: AttachDialogProcessItem?) {
      selectedItem = item
      debuggers = item?.groupsWithItems?.flatMap { groupToItems ->
        groupToItems.second.flatMap { item ->
          item.debuggers.map {
            AttachDebuggerAction(it, item)
          }
        }
      } ?: emptyList()
      onFilterUpdated()
    }

    private fun getActiveDebuggerAction(): AttachDebuggerAction? {
      return debuggers.firstOrNull { it.debugger == selectedItem?.getMainDebugger(state) }
    }

    fun onFilterUpdated() {
      debuggers.forEach { debugger -> debugger.isMainAction = false }
      val activeDebuggerAction = getActiveDebuggerAction()
      activeDebuggerAction?.isMainAction = true
      isEnabled = activeDebuggerAction != null
      putValue(NAME, activeDebuggerAction?.debugger.getActionPresentation())
    }

    override fun actionPerformed(e: ActionEvent?) {
      getActiveDebuggerAction()?.actionPerformed(e)
    }

    override fun getOptions(): Array<Action> {
      val actions: Array<Action> = debuggers.toTypedArray()
      return if (actions.size > 1) actions else emptyArray()
    }
  }

  private inner class AttachDebuggerAction(
    val debugger: XAttachDebugger,
    private val item: AttachToProcessItem): AbstractAction(), DumbAware {

    var isMainAction: Boolean = false

    init {
      putValue(NAME, debugger.getActionPresentation())
    }

    override fun actionPerformed(e: ActionEvent?) {
      AttachDialogStatisticsCollector.attachButtonPressed(
        debugger.javaClass,
        isMainAction,
        state.selectedViewType.get(),
        state.selectedDebuggersFilter.get() != AttachDialogAllDebuggersFilter,
        state.searchFieldValue.get().isNotBlank())
      attach(debugger, item)
    }
  }

  override fun getHelpId(): String? {
    return "debugging.AttachToProcessDialog"
  }
}