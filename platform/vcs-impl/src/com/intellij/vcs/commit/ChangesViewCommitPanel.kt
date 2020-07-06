// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit

import com.intellij.openapi.Disposable
import com.intellij.openapi.MnemonicHelper
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.editor.colors.EditorColorsListener
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.ComponentContainer
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.SystemInfo.isMac
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.VcsBundle.message
import com.intellij.openapi.vcs.changes.*
import com.intellij.openapi.vcs.changes.ui.*
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode.UNVERSIONED_FILES_TAG
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager.Companion.LOCAL_CHANGES
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager.Companion.getToolWindowFor
import com.intellij.openapi.vcs.changes.ui.VcsTreeModelData.*
import com.intellij.openapi.vcs.checkin.CheckinHandler
import com.intellij.openapi.vcs.ui.CommitMessage
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.openapi.wm.ToolWindow
import com.intellij.ui.IdeBorderFactory.createBorder
import com.intellij.ui.JBColor
import com.intellij.ui.SideBorder
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.JBOptionButton
import com.intellij.ui.components.JBOptionButton.Companion.getDefaultShowPopupShortcut
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.panels.HorizontalLayout
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.util.EventDispatcher
import com.intellij.util.IJSwingUtilities.updateComponentTreeUI
import com.intellij.util.ui.JBUI.Borders.empty
import com.intellij.util.ui.JBUI.Borders.emptyLeft
import com.intellij.util.ui.JBUI.Panels.simplePanel
import com.intellij.util.ui.JBUI.scale
import com.intellij.util.ui.UIUtil.getTreeBackground
import com.intellij.util.ui.components.BorderLayoutPanel
import com.intellij.util.ui.tree.TreeUtil.*
import com.intellij.vcs.log.VcsUser
import java.awt.LayoutManager
import java.awt.Point
import java.awt.event.ActionEvent
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.*
import javax.swing.KeyStroke.getKeyStroke
import javax.swing.border.Border
import javax.swing.border.EmptyBorder
import kotlin.properties.Delegates.observable

private val CTRL_ENTER = KeyboardShortcut(getKeyStroke(KeyEvent.VK_ENTER, InputEvent.CTRL_DOWN_MASK), null)
private val META_ENTER = KeyboardShortcut(getKeyStroke(KeyEvent.VK_ENTER, InputEvent.META_DOWN_MASK), null)
private val DEFAULT_COMMIT_ACTION_SHORTCUT: ShortcutSet =
  if (isMac) CustomShortcutSet(CTRL_ENTER, META_ENTER) else CustomShortcutSet(CTRL_ENTER)
fun getDefaultCommitShortcut() = DEFAULT_COMMIT_ACTION_SHORTCUT

private fun panel(layout: LayoutManager): JBPanel<*> = JBPanel<JBPanel<*>>(layout)

fun showEmptyCommitMessageConfirmation() = Messages.YES == Messages.showYesNoDialog(
  message("confirmation.text.check.in.with.empty.comment"),
  message("confirmation.title.check.in.with.empty.comment"),
  Messages.getWarningIcon()
)

fun JBOptionButton.getBottomInset(): Int =
  border?.getBorderInsets(this)?.bottom
  ?: (components.firstOrNull() as? JComponent)?.insets?.bottom
  ?: 0

private fun JBPopup.showAbove(component: JComponent) {
  val northWest = RelativePoint(component, Point())

  addListener(object : JBPopupListener {
    override fun beforeShown(event: LightweightWindowEvent) {
      val popup = event.asPopup()
      val location = Point(popup.locationOnScreen).apply { y = northWest.screenPoint.y - popup.size.height }

      popup.setLocation(location)
    }
  })
  show(northWest)
}

internal fun ChangesBrowserNode<*>.subtreeRootObject(): Any? = (path.getOrNull(1) as? ChangesBrowserNode<*>)?.userObject

open class ChangesViewCommitPanel(private val changesView: ChangesListView, private val rootComponent: JComponent) :
  BorderLayoutPanel(), ChangesViewCommitWorkflowUi, EditorColorsListener, ComponentContainer, DataProvider {

  private val project get() = changesView.project

  private val dataProviders = mutableListOf<DataProvider>()

  private val executorEventDispatcher = EventDispatcher.create(CommitExecutorListener::class.java)
  private val inclusionEventDispatcher = EventDispatcher.create(InclusionListener::class.java)

  private val centerPanel = simplePanel()
  private val buttonPanel = simplePanel().apply { isOpaque = false }
  private val toolbarPanel = simplePanel().apply { isOpaque = false }
  private var verticalToolbarBorder: Border? = null
  private val actions = ActionManager.getInstance().getAction("ChangesView.CommitToolbar") as ActionGroup
  private val toolbar = ActionManager.getInstance().createActionToolbar(COMMIT_TOOLBAR_PLACE, actions, false).apply {
    setTargetComponent(this@ChangesViewCommitPanel)
    component.isOpaque = false
  }
  private val commitActionToolbar =
    ActionManager.getInstance().createActionToolbar(
      ActionPlaces.UNKNOWN,
      DefaultActionGroup(ActionManager.getInstance().getAction("Vcs.ToggleAmendCommitMode")),
      true
    ).apply {
      setTargetComponent(this@ChangesViewCommitPanel)
      setReservePlaceAutoPopupIcon(false)
      component.isOpaque = false
      component.border = emptyLeft(6)
    }

  private val commitMessage = CommitMessage(project, false, false, true).apply {
    editorField.addSettingsProvider { it.setBorder(emptyLeft(6)) }
    editorField.setPlaceholder(message("commit.message.placeholder"))
  }
  private val defaultCommitAction = object : AbstractAction() {
    override fun actionPerformed(e: ActionEvent) = fireDefaultExecutorCalled()
  }
  private val commitButton = object : JBOptionButton(defaultCommitAction, emptyArray()) {
    init {
      background = getButtonPanelBackground()
      optionTooltipText = getDefaultTooltip()
      isOkToProcessDefaultMnemonics = false
    }

    override fun isDefaultButton(): Boolean = IdeFocusManager.getInstance(project).getFocusedDescendantFor(rootComponent) != null
  }
  private val commitAuthorComponent = CommitAuthorComponent(project)

  private var needUpdateCommitOptionsUi = false

  private var isHideToolWindowOnDeactivate = false

  var isToolbarHorizontal: Boolean by observable(false) { _, oldValue, newValue ->
    if (oldValue != newValue) {
      addToolbar(newValue) // this also removes toolbar from previous parent
    }
  }

  init {
    Disposer.register(this, commitMessage)

    buildLayout()
    for (support in EditChangelistSupport.EP_NAME.getExtensions(project)) {
      support.installSearch(commitMessage.editorField, commitMessage.editorField)
    }

    with(changesView) {
      setInclusionListener { inclusionEventDispatcher.multicaster.inclusionChanged() }
      isShowCheckboxes = true
    }

    setupShortcuts(rootComponent)
  }

  private fun buildLayout() {
    buttonPanel.apply {
      border = getButtonPanelBorder()

      addToLeft(commitButton)
      addToRight(NonOpaquePanel(HorizontalLayout(0)).apply {
        add(commitActionToolbar.component)
        add(toolbarPanel)
      })
    }
    centerPanel
      .addToCenter(commitMessage)
      .addToBottom(panel(VerticalLayout(0)).apply {
        background = getButtonPanelBackground()

        add(commitAuthorComponent.apply { border = empty(0, 5, 4, 0) })
        add(ChangesViewCommitStatusPanel(changesView, this@ChangesViewCommitPanel, getButtonPanelBackground()))
        add(buttonPanel)
      })
    addToCenter(centerPanel)
    addToolbar(isToolbarHorizontal)

    withPreferredHeight(85)
  }

  private fun addToolbar(isHorizontal: Boolean) {
    if (isHorizontal) {
      toolbar.setOrientation(SwingConstants.HORIZONTAL)
      toolbar.setReservePlaceAutoPopupIcon(false)
      verticalToolbarBorder = toolbar.component.border
      toolbar.component.border = null

      centerPanel.border = null
      toolbarPanel.addToCenter(toolbar.component)
    }
    else {
      toolbar.setOrientation(SwingConstants.VERTICAL)
      toolbar.setReservePlaceAutoPopupIcon(true)
      verticalToolbarBorder?.let { toolbar.component.border = it }

      centerPanel.border = createBorder(JBColor.border(), SideBorder.LEFT)
      addToLeft(toolbar.component)
    }
  }

  private fun getButtonPanelBorder(): Border =
    EmptyBorder(0, scale(3), (scale(6) - commitButton.getBottomInset()).coerceAtLeast(0), 0)

  private fun getButtonPanelBackground() =
    JBColor { (commitMessage.editorField.editor as? EditorEx)?.backgroundColor ?: getTreeBackground() }

  private fun fireDefaultExecutorCalled() = executorEventDispatcher.multicaster.executorCalled(null)

  private fun setupShortcuts(component: JComponent) {
    DefaultCommitAction().registerCustomShortcutSet(DEFAULT_COMMIT_ACTION_SHORTCUT, component, this)
    ShowCustomCommitActions().registerCustomShortcutSet(getDefaultShowPopupShortcut(), component, this)
  }

  override fun globalSchemeChange(scheme: EditorColorsScheme?) {
    needUpdateCommitOptionsUi = true
    buttonPanel.border = getButtonPanelBorder()
  }

  override val commitMessageUi: CommitMessageUi get() = commitMessage

  // NOTE: getter should return text with mnemonic (if any) to make mnemonics available in dialogs shown by commit handlers.
  //  See CheckinProjectPanel.getCommitActionName() usages.
  override var defaultCommitActionName: String
    get() = (defaultCommitAction.getValue(Action.NAME) as? String).orEmpty()
    set(value) = defaultCommitAction.putValue(Action.NAME, value)

  override var isDefaultCommitActionEnabled: Boolean
    get() = defaultCommitAction.isEnabled
    set(value) {
      defaultCommitAction.isEnabled = value
    }

  override fun setCustomCommitActions(actions: List<AnAction>) = commitButton.setOptions(actions)

  override var commitAuthor: VcsUser?
    get() = commitAuthorComponent.commitAuthor
    set(value) {
      commitAuthorComponent.commitAuthor = value
    }

  override fun addCommitAuthorListener(listener: CommitAuthorListener, parent: Disposable) =
    commitAuthorComponent.addCommitAuthorListener(listener, parent)

  override var editedCommit by observable<EditedCommitDetails?>(null) { _, _, newValue ->
    refreshData()
    newValue?.let { expand(it) }
  }

  override val isActive: Boolean get() = isVisible

  override fun activate(): Boolean {
    val toolWindow = getVcsToolWindow() ?: return false
    val contentManager = ChangesViewContentManager.getInstance(project)

    saveToolWindowState()
    changesView.isShowCheckboxes = true
    isVisible = true

    selectContent(contentManager)
    toolWindow.activate({ commitMessage.requestFocusInMessage() }, false)
    return true
  }

  protected open fun selectContent(contentManager: ChangesViewContentI) = contentManager.selectContent(LOCAL_CHANGES)

  override fun deactivate(isRestoreState: Boolean) {
    if (isRestoreState) restoreToolWindowState()
    clearToolWindowState()
    changesView.isShowCheckboxes = false
    isVisible = false
  }

  private fun saveToolWindowState() {
    if (!isActive) {
      isHideToolWindowOnDeactivate = getVcsToolWindow()?.isVisible != true
    }
  }

  private fun restoreToolWindowState() {
    if (isHideToolWindowOnDeactivate) {
      getVcsToolWindow()?.hide(null)
    }
  }

  private fun clearToolWindowState() {
    isHideToolWindowOnDeactivate = false
  }

  protected open fun getVcsToolWindow(): ToolWindow? = getToolWindowFor(project, LOCAL_CHANGES)

  override fun expand(item: Any) {
    val node = changesView.findNodeInTree(item)
    node?.let { changesView.expandSafe(it) }
  }

  override fun select(item: Any) {
    val path = changesView.findNodePathInTree(item)
    path?.let { selectPath(changesView, it, false) }
  }

  override fun selectFirst(items: Collection<Any>) {
    if (items.isEmpty()) return

    val path = treePathTraverser(changesView).preOrderDfsTraversal().find { getLastUserObject(it) in items }
    path?.let { selectPath(changesView, it, false) }
  }

  override fun showCommitOptions(options: CommitOptions, actionName: String, isFromToolbar: Boolean, dataContext: DataContext) {
    val commitOptionsPanel = CommitOptionsPanel { actionName }.apply {
      focusTraversalPolicy = LayoutFocusTraversalPolicy()
      isFocusCycleRoot = true

      setOptions(options)
      border = empty(0, 10)
      MnemonicHelper.init(this)

      // to reflect LaF changes as commit options components are created once per commit
      if (needUpdateCommitOptionsUi) {
        needUpdateCommitOptionsUi = false
        updateComponentTreeUI(this)
      }
    }
    val focusComponent = IdeFocusManager.getInstance(project).getFocusTargetFor(commitOptionsPanel)
    val commitOptionsPopup = JBPopupFactory.getInstance()
      .createComponentPopupBuilder(commitOptionsPanel, focusComponent)
      .setRequestFocus(true)
      .createPopup()

    commitOptionsPopup.show(isFromToolbar, dataContext)
  }

  private fun JBPopup.show(isFromToolbar: Boolean, dataContext: DataContext) =
    when {
      isFromToolbar && isToolbarHorizontal -> showAbove(toolbar.component)
      isFromToolbar && !isToolbarHorizontal -> showAbove(this@ChangesViewCommitPanel)
      else -> showInBestPositionFor(dataContext)
    }

  override fun setCompletionContext(changeLists: List<LocalChangeList>) {
    commitMessage.changeLists = changeLists
  }

  override fun getComponent(): JComponent = this
  override fun getPreferredFocusableComponent(): JComponent = commitMessage.editorField

  override fun getData(dataId: String) = getDataFromProviders(dataId) ?: commitMessage.getData(dataId)
  fun getDataFromProviders(dataId: String) = dataProviders.asSequence().mapNotNull { it.getData(dataId) }.firstOrNull()

  override fun addDataProvider(provider: DataProvider) {
    dataProviders += provider
  }

  override fun addExecutorListener(listener: CommitExecutorListener, parent: Disposable) =
    executorEventDispatcher.addListener(listener, parent)

  override fun refreshData() = ChangesViewManager.getInstanceEx(project).refreshImmediately()

  override fun getDisplayedChanges(): List<Change> = all(changesView).userObjects(Change::class.java)
  override fun getIncludedChanges(): List<Change> = included(changesView).userObjects(Change::class.java)

  override fun getDisplayedUnversionedFiles(): List<FilePath> =
    allUnderTag(changesView, UNVERSIONED_FILES_TAG).userObjects(FilePath::class.java)

  override fun getIncludedUnversionedFiles(): List<FilePath> =
    includedUnderTag(changesView, UNVERSIONED_FILES_TAG).userObjects(FilePath::class.java)

  override var inclusionModel: InclusionModel?
    get() = changesView.inclusionModel
    set(value) {
      changesView.setInclusionModel(value)
    }

  override fun includeIntoCommit(items: Collection<*>) = changesView.includeChanges(items)

  override fun addInclusionListener(listener: InclusionListener, parent: Disposable) =
    inclusionEventDispatcher.addListener(listener, parent)

  override fun confirmCommitWithEmptyMessage(): Boolean = showEmptyCommitMessageConfirmation()

  override fun startBeforeCommitChecks() = Unit
  override fun endBeforeCommitChecks(result: CheckinHandler.ReturnResult) = Unit

  override fun dispose() {
    with(changesView) {
      isShowCheckboxes = false
      setInclusionListener(null)
    }
  }

  inner class DefaultCommitAction : DumbAwareAction() {
    override fun update(e: AnActionEvent) {
      e.presentation.isEnabledAndVisible = isActive && defaultCommitAction.isEnabled
    }

    override fun actionPerformed(e: AnActionEvent) = fireDefaultExecutorCalled()
  }

  private inner class ShowCustomCommitActions : DumbAwareAction() {
    override fun update(e: AnActionEvent) {
      e.presentation.isEnabledAndVisible = isActive && commitButton.isEnabled
    }

    override fun actionPerformed(e: AnActionEvent) = commitButton.showPopup()
  }

  companion object {
    internal const val COMMIT_TOOLBAR_PLACE: String = "ChangesView.CommitToolbar"
  }
}