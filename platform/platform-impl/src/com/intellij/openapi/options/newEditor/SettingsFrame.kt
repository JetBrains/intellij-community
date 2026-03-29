// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.options.newEditor

import com.intellij.CommonBundle
import com.intellij.diagnostic.LoadingState
import com.intellij.icons.AllIcons
import com.intellij.ide.HelpTooltip
import com.intellij.ide.IdeBundle
import com.intellij.ide.ProjectWindowCustomizerService
import com.intellij.ide.SaveAndSyncHandler
import com.intellij.ide.plugins.newui.EventHandler
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonShortcuts
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.help.HelpManager
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurableGroup
import com.intellij.openapi.options.ex.ConfigurableExtensionPointUtil
import com.intellij.openapi.options.ex.ConfigurableVisitor
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectCloseListener
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.FrameWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.OnePixelDivider
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.WindowState
import org.jetbrains.annotations.ApiStatus
import com.intellij.openapi.ui.ComboBoxWithWidePopup
import com.intellij.ui.DeferredIcon
import com.intellij.ui.DeferredIconImpl
import com.intellij.ui.DeferredIconListener
import com.intellij.ui.MutableCollectionComboBoxModel
import com.intellij.ui.dsl.listCellRenderer.listCellRenderer
import com.intellij.ui.SearchTextField
import com.intellij.ui.border.CustomLineBorder
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.ui.mac.touchbar.Touchbar
import com.intellij.util.IconUtil
import com.intellij.util.ui.DialogUtil
import com.intellij.util.ui.GridBag
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.EventQueue
import java.awt.Frame
import java.awt.GridBagLayout
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import java.awt.event.ItemEvent
import java.awt.event.KeyEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.AbstractAction
import javax.swing.Box
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JFrame
import javax.swing.JPanel
import javax.swing.KeyStroke
import javax.swing.UIManager
import javax.swing.border.CompoundBorder

/**
 * Non-modal settings window using JFrame
 * One instance per application; a project combo lets the user switch between open projects.
 */
@ApiStatus.Internal
internal class SettingsFrame private constructor(
  private var project: Project,
  groups: List<ConfigurableGroup>,
  configurable: Configurable?,
  filter: String?,
) : FrameWrapper(project, "SettingsEditor"), UiDataProvider {

  companion object {
    /** Single app-wide instance. */
    @Volatile
    private var ourInstance: SettingsFrame? = null

    private const val PINNED_KEY = "ide.settings.window.pinned"

    private fun resetInstance() {
      ourInstance = null
    }

    /**
     * Returns the single app-wide Settings frame, creating it if necessary.
     * If it already exists for a different project, switches to that project
     * (the user may be prompted to save/discard pending changes).
     */
    @JvmStatic
    fun getOrCreate(
      project: Project,
      groups: List<ConfigurableGroup>,
      configurable: Configurable?,
      filter: String?,
    ): SettingsFrame {
      val existing = ourInstance
      if (existing != null && !existing.isDisposed) {
        if (existing.project == project) {
          configurable?.let { existing.editor.select(it) }
        }
        else {
          existing.switchToProject(project, configurable)
        }
        return existing
      }
      return SettingsFrame(project, groups, configurable, filter).also { ourInstance = it }
    }

    private fun openProjects(): Array<Project> =
      ProjectManager.getInstance().openProjects.filter { !it.isDefault }.toTypedArray()

    private val spotlightPainterFactory = object : SpotlightPainterFactory {
      override fun createSpotlightPainter(
        project: Project, target: JComponent, parent: Disposable, updater: (SpotlightPainter) -> Unit,
      ): SpotlightPainter = SpotlightPainter(target, updater)
    }
  }

  /** Lifetime of this frame (disposed when the frame closes). */
  private val frameDisposable: Disposable = Disposer.newDisposable("SettingsFrame")

  // ── UI components ─────────────────────────────────────────────────────────────

  /** Panel that holds the editor; its content is swapped on project switch. */
  private val contentArea = JPanel(BorderLayout())

  /** Combo box for switching between open projects. */
  private lateinit var projectWidget: ComboBoxWithWidePopup<Project>

  /** The project-switcher panel — created once and re-attached to each new editor on switch. */
  private lateinit var projectWidgetPanel: JPanel

  /** True while programmatically updating the project widget; suppresses re-entrant switches. */
  private var suppressProjectSwitch = false

  /** Kept so we can rewire it to the new editor when switching projects. */
  private var applyButton: JButton? = null

  /** The editor for the current project. Replaced when switching projects. */
  private var editor: SettingsEditor

  /** Disposable for the current editor. Disposed and recreated on each project switch. */
  private var editorDisposable: Disposable

  /** Whether the settings window floats above the IDE (always on top). Persisted across sessions. */
  private var isPinned: Boolean
    get() = PropertiesComponent.getInstance().getBoolean(PINNED_KEY, true)
    set(value) { PropertiesComponent.getInstance().setValue(PINNED_KEY, value, true) }

  // ─────────────────────────────────────────────────────────────────────────────

  init {
    title = CommonBundle.settingsTitle()

    editorDisposable = Disposer.newDisposable("SettingsFrame.editor")
    Disposer.register(frameDisposable, editorDisposable)

    editor = makeEditor(editorDisposable, project, groups, configurable, filter)

    contentArea.add(editor, BorderLayout.CENTER)
    editor.setTreeTopComponent(createProjectWidget())

    val mainPanel = JPanel(BorderLayout())
    mainPanel.add(contentArea, BorderLayout.CENTER)
    mainPanel.add(createButtonPanel(), BorderLayout.SOUTH)

    component = mainPanel
    preferredFocusedComponent = editor.getPreferredFocusedComponent()

    val initialSize = editor.getDialogInitialSize()
    if (initialSize != null && initialSize.width > 0 && initialSize.height > 0) {
      setSize(initialSize)
    }
    else {
      setSize(Dimension(900, 700))
    }
    getFrame().minimumSize = Dimension(900, 700)

    // Register keyboard shortcuts and window-close behavior.
    val window = getFrame()
    if (window is JFrame) {
      val rootPane = window.rootPane
      window.isAlwaysOnTop = isPinned

      // Cmd/Ctrl+F: open search
      EventHandler.getShortcuts(IdeActions.ACTION_FIND)?.let { findShortcut ->
        SearchTextField.FindAction().registerCustomShortcutSet(findShortcut, rootPane, frameDisposable)
      }

      // ESC / Cmd-W: mirror SettingsDialog.doCancelAction — delegate to editor.cancel() so that
      // pressing ESC while a search filter is active clears the filter instead of closing the frame.
      val closeHandler = ActionListener { e ->
        val current = EventQueue.getCurrentEvent()
        if (editor.cancel(current as? KeyEvent ?: e)) close()
      }
      rootPane.registerKeyboardAction(closeHandler, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                                      JComponent.WHEN_IN_FOCUSED_WINDOW)
      ActionUtil.registerForEveryKeyboardShortcut(rootPane, closeHandler, CommonShortcuts.getCloseActiveWindow())

      // X button: cancel modified configurables before FrameWrapper closes the window (same as SettingsDialog).
      window.addWindowListener(object : WindowAdapter() {
        override fun windowClosing(e: WindowEvent) {
          editor.cancel(e)
        }
      })

      // When the window deactivates, clear the root pane's default button so the OK button
      // loses its blue "active" appearance; restore it when the window re-activates.
      // Also, repaint the full window so other components reflect the inactive state.
      window.addWindowListener(object : WindowAdapter() {
        private var savedDefaultButton: JButton? = null

        override fun windowDeactivated(e: WindowEvent) {
          savedDefaultButton = rootPane.defaultButton ?: return
          rootPane.defaultButton = null
          window.repaint()
        }

        override fun windowActivated(e: WindowEvent) {
          val button = savedDefaultButton ?: return
          rootPane.defaultButton = button
          savedDefaultButton = null
          window.repaint()
        }
      })
    }

    val bus = ApplicationManager.getApplication().messageBus

    // Handle current-project closure
    bus.connect(frameDisposable)
      .subscribe(ProjectCloseListener.TOPIC, object : ProjectCloseListener {
        override fun projectClosed(project: Project) {
          if (project == this@SettingsFrame.project) {
            val remaining = openProjects()
            if (remaining.isNotEmpty()) {
              doSwitchProject(remaining[0], null)
            }
            else {
              close()
            }
          }
        }
      })

    // Repaint the project combo box when a deferred project icon finishes loading.
    // The renderer calls retrieveIcon() on the DeferredIconImpl to get the concrete icon, which
    // is a placeholder until loading completes. This subscription triggers a repaint, so the
    // resolved icon is shown as soon as it is ready.
    bus.connect(frameDisposable)
      .subscribe(DeferredIconListener.TOPIC, object : DeferredIconListener {
        override fun evaluated(deferred: DeferredIcon, result: Icon) = projectWidget.repaint()
      })
  }

  // ── Editor factory ────────────────────────────────────────────────────────────

  private fun makeEditor(
    disposable: Disposable,
    project: Project,
    groups: List<ConfigurableGroup>,
    configurable: Configurable?,
    filter: String?,
  ): SettingsEditor = SettingsEditor(
    disposable, project, groups, configurable, filter,
    true,  // useLeaveState: auto-reset unmodified configurables when navigating back
    ISettingsTreeViewFactory { f, g -> SettingsTreeView(f, g) },
    spotlightPainterFactory,
    createPinAction(),
  )

  // ── Project widget ────────────────────────────────────────────────────────────

  /** Creates [projectWidget] and [projectWidgetPanel] once during initialization. */
  private fun createProjectWidget(): JPanel {
    val iconSize = JBUI.scale(16)
    val customizer = ApplicationManager.getApplication().getService(ProjectWindowCustomizerService::class.java)
    val model = MutableCollectionComboBoxModel(openProjects().toMutableList())
    projectWidget = ComboBoxWithWidePopup(model)
    projectWidget.renderer = listCellRenderer<Project?> {
      value?.let { proj ->
        val rawIcon = customizer.getProjectIcon(proj)
        // DeferredIcon.deepCopy() resets scaledDelegateIcon to the placeholder even when isDone=true,
        // so passing a DeferredIcon to downscaleIconToSize permanently shows blanks after loading.
        // Instead, retrieve the currently resolved icon and scale the concrete result.
        val concreteIcon = if (rawIcon is DeferredIconImpl<*>) {
          rawIcon.triggerEvaluation()
          rawIcon.retrieveIcon()
        }
        else rawIcon
        icon(IconUtil.downscaleIconToSize(concreteIcon, iconSize, iconSize))
        text(proj.name)
      }
    }
    model.selectedItem = project
    projectWidget.addItemListener { e ->
      if (!suppressProjectSwitch && e.stateChange == ItemEvent.SELECTED) {
        val selected = e.item as? Project ?: return@addItemListener
        if (selected != project) switchToProject(selected, null)
      }
    }

    projectWidgetPanel = object : JPanel(BorderLayout()) {
      override fun getMinimumSize(): Dimension = Dimension(0, super.getMinimumSize().height)
    }
    projectWidgetPanel.background = UIUtil.SIDE_PANEL_BACKGROUND
    projectWidgetPanel.border = JBUI.Borders.empty(4, 5, 0, 5)
    projectWidgetPanel.add(projectWidget, BorderLayout.CENTER)
    return projectWidgetPanel
  }

  private fun createPinAction(): ToggleAction = object : ToggleAction(
    IdeBundle.messagePointer("action.ToggleAction.text.pin.window"),
    IdeBundle.messagePointer("action.ToggleAction.description.pin.window"),
    AllIcons.General.Pin_tab,
  ) {
    override fun isSelected(e: AnActionEvent): Boolean = isPinned
    override fun setSelected(e: AnActionEvent, state: Boolean) {
      isPinned = state
      getFrame().isAlwaysOnTop = state
    }
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
    override fun isDumbAware(): Boolean = true
  }

  /**
   * Refreshes the project widget's item list and selection to reflect [newProject].
   * Uses [suppressProjectSwitch] to prevent the [ItemEvent] from triggering a re-entrant switch.
   */
  private fun refreshProjectWidget(newProject: Project) {
    suppressProjectSwitch = true
    try {
      @Suppress("UNCHECKED_CAST")
      (projectWidget.model as MutableCollectionComboBoxModel<Project>).update(openProjects().toList())
      projectWidget.model.selectedItem = newProject
    }
    finally {
      suppressProjectSwitch = false
    }
  }

  // ── Project switching ─────────────────────────────────────────────────────────

  /**
   * Asks the user what to do with unsaved changes, then switches to [newProject].
   */
  private fun switchToProject(newProject: Project, toSelect: Configurable?) {
    if (editor.isModified) {
      val choice = Messages.showYesNoCancelDialog(
        getFrame(),
        ApplicationBundle.message("settings.switch.project.unsaved.message", project.name, newProject.name),
        ApplicationBundle.message("settings.switch.project.unsaved.title"),
        ApplicationBundle.message("settings.switch.project.button.apply"),
        ApplicationBundle.message("settings.switch.project.button.dont.save"),
        CommonBundle.getCancelButtonText(),
        Messages.getWarningIcon(),
      )

      if (choice == Messages.CANCEL) {
        refreshProjectWidget(project)
        return
      }
      if (choice == Messages.YES) {
        if (!editor.apply()) {
          refreshProjectWidget(project)
          return // validation failed – stay on current project
        }
      }
      // Messages.NO → discard, fall through
    }
    doSwitchProject(newProject, toSelect)
  }

  /** Unconditionally replaces the editor with one for [newProject]. */
  private fun doSwitchProject(newProject: Project, toSelect: Configurable?) {
    project = newProject

    // Capture UI state before disposing the old editor
    val currentConfigurableId = editor.currentConfigurable
      ?.let { ConfigurableVisitor.getId(it) }
    val splitterProportion = editor.splitterProportion

    // Remove old editor from UI before disposing it
    contentArea.remove(editor)

    // Dispose old editor disposable (removes it from frameDisposable automatically)
    Disposer.dispose(editorDisposable)

    // Build configurable groups for the new project
    val group = ConfigurableExtensionPointUtil.getConfigurableGroup(newProject, true)
    val groups = if (group.configurables.isNotEmpty()) listOf(group) else emptyList()

    // Explicit request > same page in new project > default
    val resolvedToSelect = toSelect
      ?: currentConfigurableId?.let { ConfigurableVisitor.findById(it, groups) }

    // Fresh editor disposable parented to the frame
    editorDisposable = Disposer.newDisposable("SettingsFrame.editor")
    Disposer.register(frameDisposable, editorDisposable)

    editor = makeEditor(editorDisposable, newProject, groups, resolvedToSelect, null)

    refreshProjectWidget(newProject)
    contentArea.add(editor, BorderLayout.CENTER)
    editor.setTreeTopComponent(projectWidgetPanel)
    editor.splitterProportion = splitterProportion
    contentArea.revalidate()
    contentArea.repaint()

    // Rewire Apply button to the new editor's action
    applyButton?.let { btn ->
      editor.getApplyAction()?.let { action -> btn.action = action }
    }

    // preferredFocusedComponent only takes effect on first show; request focus explicitly here.
    EventQueue.invokeLater { editor.getPreferredFocusedComponent()?.requestFocusInWindow() }
  }

  // ── FrameWrapper overrides ────────────────────────────────────────────────────

  override fun uiDataSnapshot(sink: DataSink) {
    sink.uiDataSnapshot(editor)
  }

  override fun loadFrameState(state: WindowState?) {
    val window = getFrame()
    val currentSize = window.size

    if (state != null) {
      state.applyTo(window)
      val savedSize = window.size
      if (savedSize.width < 400 || savedSize.height < 300) {
        window.size = currentSize // saved state has an invalid size
      }
    }
    else {
      super.loadFrameState(state)
      if (currentSize.width > 0 && currentSize.height > 0) {
        window.size = currentSize
      }
    }
    // Create the native peer (addNotify) for HiDPI-aware rendering, then run a layout
    // pass (validate) so components like the project icon are sized correctly on first show.
    window.addNotify()
    window.validate()
  }

  override fun show() {
    val window = getFrame()
    if (window.isVisible) {
      if (window is Frame) {
        if ((window.extendedState and Frame.ICONIFIED) != 0) {
          window.extendedState = window.extendedState and Frame.ICONIFIED.inv()
        }
      }
      window.toFront()
      window.requestFocus()
      return
    }
    super.show()
  }

  override fun dispose() {
    try {
      resetInstance()
      Disposer.dispose(frameDisposable)
    }
    finally {
      super.dispose()
    }
  }

  // ── Button panel ──────────────────────────────────────────────────────────────

  private fun createHelpButton(): JButton {
    val helpButton = JButton(object : AbstractAction() {
      override fun actionPerformed(e: ActionEvent) {
        // Read help topic at invocation time so it reflects the current editor/configurable
        HelpManager.getInstance().invokeHelp(editor.getHelpTopic())
      }
    })
    helpButton.putClientProperty("JButton.buttonType", "help")
    helpButton.text = ""
    helpButton.margin = JBInsets.emptyInsets()
    helpButton.addPropertyChangeListener("ancestor") { evt ->
      if (evt.newValue == null) {
        HelpTooltip.dispose(evt.source as JComponent)
      }
    }
    editor.setHelpTooltip(helpButton)
    return helpButton
  }

  private fun createButtonPanel(): JPanel {
    // Matches SettingsDialog's south panel (DialogStyle.COMPACT): 1px top divider, 8×12 insets.
    val dividerColor = UIManager.getColor("DialogWrapper.southPanelDivider")
    val panel = object : JPanel(BorderLayout()) {
      override fun getBackground(): java.awt.Color? =
        UIManager.getColor("DialogWrapper.southPanelBackground") ?: super.getBackground()
    }
    panel.border = CompoundBorder(
      CustomLineBorder(dividerColor ?: OnePixelDivider.BACKGROUND, 1, 0, 0, 0),
      JBUI.Borders.empty(8, 12))

    // Use the same button factory as DialogWrapper for consistent macOS type, mnemonics, and sizing.
    val okAction = object : AbstractAction(CommonBundle.getOkButtonText()) {
      override fun actionPerformed(e: ActionEvent) {
        UIUtil.stopFocusedEditing(getFrame())
        if (editor.apply()) {
          SaveAndSyncHandler.getInstance().scheduleSave(SaveAndSyncHandler.SaveTask(null, true))
          close()
        }
      }
    }
    // Marking DEFAULT_ACTION causes createJButtonForAction to register the button as the root-pane default.
    okAction.putValue(DialogWrapper.DEFAULT_ACTION, true)

    val window = getFrame()
    val okButton = DialogWrapper.createJButtonForAction(okAction, (window as? JFrame)?.rootPane)

    val cancelButton = DialogWrapper.createJButtonForAction(
      object : AbstractAction(CommonBundle.getCancelButtonText()) {
        override fun actionPerformed(e: ActionEvent) {
          if (editor.cancel(e)) close()
        }
      }, null)

    val applyAction = editor.getApplyAction()
    if (applyAction != null) {
      applyButton = DialogWrapper.createJButtonForAction(applyAction, null)
      DialogUtil.registerMnemonic(applyButton!!)
    }

    val rightButtons = mutableListOf<JButton>()
    if (SystemInfo.isMac) {
      // macOS: Cancel | Apply | OK
      rightButtons.add(cancelButton)
      applyButton?.let { rightButtons.add(it) }
      rightButtons.add(okButton)
    }
    else {
      // Windows/Linux: OK | Cancel | Apply
      rightButtons.add(okButton)
      rightButtons.add(cancelButton)
      applyButton?.let { rightButtons.add(it) }
    }

    val leftButtons = listOf(createHelpButton())

    val lrPanel = NonOpaquePanel(GridBagLayout())
    val bag = GridBag().setDefaultInsets(JBInsets.emptyInsets())

    lrPanel.add(DialogWrapper.layoutButtonsPanel(leftButtons), bag.next())
    lrPanel.add(Box.createHorizontalGlue(), bag.next().weightx(1.0).fillCellHorizontally())
    lrPanel.add(DialogWrapper.layoutButtonsPanel(rightButtons), bag.next())

    panel.add(lrPanel, BorderLayout.CENTER)
    if (LoadingState.COMPONENTS_REGISTERED.isOccurred && ApplicationManager.getApplication() != null) {
      Touchbar.setButtonActions(panel, leftButtons, rightButtons, null)
    }
    return panel
  }
}

