// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.options.newEditor

import com.intellij.CommonBundle
import com.intellij.diagnostic.LoadingState
import com.intellij.ide.HelpTooltip
import com.intellij.ide.ProjectWindowCustomizerService
import com.intellij.ide.SaveAndSyncHandler
import com.intellij.ide.plugins.newui.EventHandler
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.CommonShortcuts
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.application.ApplicationListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.help.HelpManager
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurableGroup
import com.intellij.openapi.options.ex.ConfigurableExtensionPointUtil
import com.intellij.openapi.options.ex.ConfigurableVisitor
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectCloseListener
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.MnemonicHelper
import com.intellij.openapi.project.VetoableProjectManagerListener
import com.intellij.openapi.ui.ComboBoxWithWidePopup
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.FrameWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.OnePixelDivider
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.WindowState
import com.intellij.ui.DeferredIcon
import com.intellij.ui.DeferredIconImpl
import com.intellij.ui.DeferredIconListener
import com.intellij.ui.MutableCollectionComboBoxModel
import com.intellij.ui.SearchTextField
import com.intellij.ui.border.CustomLineBorder
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.ui.dsl.listCellRenderer.listCellRenderer
import com.intellij.util.IconUtil
import com.intellij.ui.mac.touchbar.Touchbar
import com.intellij.util.system.OS
import com.intellij.util.ui.DialogUtil
import com.intellij.util.ui.GridBag
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.ApiStatus
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.EventQueue
import java.awt.Frame
import java.awt.GridBagLayout
import java.awt.Window
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
 * Non-modal settings window using JFrame.
 * One instance per application; opening settings from a different project switches the frame to that project.
 */
@ApiStatus.Internal
open class SettingsFrame @ApiStatus.Internal constructor(
  private var project: Project,
  groups: List<ConfigurableGroup>,
  configurable: Configurable?,
  filter: String?,
) : FrameWrapper(null, "SettingsEditor"), UiDataProvider {

  companion object {
    /** Single app-wide instance. */
    @Volatile
    private var ourInstance: SettingsFrame? = null

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
      frameFactory: ((Project, List<ConfigurableGroup>, Configurable?, String?) -> SettingsFrame)? = null,
    ): SettingsFrame {
      val existing = ourInstance
      if (existing != null && !existing.isDisposed) {
        if (existing.project == project) {
          if (configurable != null) {
            existing.editor.selectWithFilter(configurable, filter)
          } else {
            filter?.let { existing.editor.setFilter(it) }
          }
        }
        else {
          existing.switchToProject(project, configurable, filter)
        }
        return existing
      }
      return (frameFactory?.invoke(project, groups, configurable, filter)
              ?: SettingsFrame(project, groups, configurable, filter)).also { ourInstance = it }
    }

    private fun openProjects(): List<Project> =
      ProjectManager.getInstance().openProjects.filter { !it.isDefault }

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

  /** Kept so we can rewire it to the new editor when switching projects. */
  private var applyButton: JButton? = null

  /** Combo box for switching between open projects. */
  private var projectWidget: ComboBoxWithWidePopup<Project>? = null

  /** The project-switcher panel — created once and re-attached to each new editor on switch. */
  private var projectWidgetPanel: JPanel? = null

  /** True, while programmatically updating the project widget; suppresses re-entrant switches. */
  private var suppressProjectSwitch = false

  /** The editor for the current project. Replaced when switching projects. */
  private var editor: SettingsEditor

  /** Disposable for the current editor. Disposed and recreated on each project switch. */
  private var editorDisposable: Disposable

  // ─────────────────────────────────────────────────────────────────────────────

  /** Returns the project currently shown in this frame. */
  protected fun getProject(): Project = project

  /** Called once, right after the frame first becomes visible. */
  protected open fun onShow() {}

  /** Called after [SettingsEditor.apply] succeeds and before [close]. */
  protected open fun afterApply() {}

  /** Called after settings are cancelled (Cancel button / ESC / window-X) and before [close]. */
  protected open fun afterCancel() {}

  // ─────────────────────────────────────────────────────────────────────────────

  init {
    title = CommonBundle.settingsTitle()

    editorDisposable = Disposer.newDisposable("SettingsFrame.editor")
    Disposer.register(frameDisposable, editorDisposable)

    editor = makeEditor(editorDisposable, project, groups, configurable, filter)

    contentArea.add(editor, BorderLayout.CENTER)

    val mainPanel = JPanel(BorderLayout())
    mainPanel.add(contentArea, BorderLayout.CENTER)
    mainPanel.add(createButtonPanel(), BorderLayout.SOUTH)

    editor.setTreeTopComponent(createProjectWidget())

    component = mainPanel
    MnemonicHelper.init(mainPanel)
    preferredFocusedComponent = editor.getPreferredFocusedComponent()

    val initialSize = editor.getDialogInitialSize()
    if (initialSize != null && initialSize.width > 0 && initialSize.height > 0) {
      setSize(initialSize)
    }
    else {
      setSize(JBUI.size(900, 700))
    }
    getFrame().minimumSize = JBUI.size(900, 700)

    // Register keyboard shortcuts and window-close behavior.
    val window = getFrame()
    if (window is JFrame) {
      val rootPane = window.rootPane
      // Cmd/Ctrl+F: open search
      EventHandler.getShortcuts(IdeActions.ACTION_FIND)?.let { findShortcut ->
        SearchTextField.FindAction().registerCustomShortcutSet(findShortcut, rootPane, frameDisposable)
      }

      // ESC / Cmd-W: mirror SettingsDialog.doCancelAction — delegate to editor.cancel() so that
      // pressing ESC while a search filter is active clears the filter instead of closing the frame.
      val closeHandler = ActionListener { e ->
        val current = EventQueue.getCurrentEvent()
        if (editor.cancel(current as? KeyEvent ?: e)) {
          afterCancel()
          close()
        }
      }
      rootPane.registerKeyboardAction(closeHandler, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                                      JComponent.WHEN_IN_FOCUSED_WINDOW)
      ActionUtil.registerForEveryKeyboardShortcut(rootPane, closeHandler, CommonShortcuts.getCloseActiveWindow())

      // X button: cancel modified configurables before FrameWrapper closes the window (same as SettingsDialog).
      window.addWindowListener(object : WindowAdapter() {
        override fun windowClosing(e: WindowEvent) {
          editor.cancel(e)
          afterCancel()
        }
      })

      // When the window deactivates, clear the root pane's default button so the OK button
      // loses its blue "active" appearance; restore it when the window re-activates.
      // Also, repaint the full window so other components reflect the inactive state.
      window.addWindowListener(object : WindowAdapter() {
        private var savedDefaultButton: JButton? = null

        override fun windowDeactivated(e: WindowEvent) {
          val button = rootPane.defaultButton
          if (button != null) {
            savedDefaultButton = button
            rootPane.defaultButton = null
            window.repaint()
          }
          // Don't snapshot leave state when focus moves to an owned child dialog: any changes
          // the user makes there are intentional, so the snapshot would be stale on return.
          if (!e.oppositeWindow.isOwnedBy(window)) {
            editor.recordWindowLeaveState()
          }
        }

        override fun windowActivated(e: WindowEvent) {
          val button = savedDefaultButton
          if (button != null) {
            rootPane.defaultButton = button
            savedDefaultButton = null
            window.repaint()
          }
          // Skip reset only when returning from an owned child dialog (e.g. a file chooser opened
          // by a configurable). All other cases — including null (browser, OS app, drag-and-drop)
          // and unrelated AWT windows — are genuine app-switches where external changes may have
          // occurred and the configurable should be refreshed.
          val opposite = e.oppositeWindow
          if (opposite == null || !opposite.isOwnedBy(window)) {
            editor.resetUnmodifiedOnWindowFocus()
          }
        }
      })
    }

    val bus = ApplicationManager.getApplication().messageBus

    // Close the frame when its project closes (no switching to another project).
    bus.connect(frameDisposable)
      .subscribe(ProjectCloseListener.TOPIC, object : ProjectCloseListener {
        override fun projectClosed(project: Project) {
          if (project == this@SettingsFrame.project) close()
        }
      })

    // Repaint the project combo box when a deferred project icon finishes loading.
    bus.connect(frameDisposable)
      .subscribe(DeferredIconListener.TOPIC, object : DeferredIconListener {
        override fun evaluated(deferred: DeferredIcon, result: Icon) { projectWidget?.repaint() }
      })

    // canExitApplication fires before any frame is hidden — the settings window is still visible
    // and the editor context is intact. Shows Apply / Don't Save / Cancel; returning false vetoes
    // the exit. canClose (below) handles the project-close-without-IDE-exit case the same way.
    ApplicationManagerEx.getApplicationEx().addApplicationListener(object : ApplicationListener {
      override fun canExitApplication(): Boolean =
        promptUnsavedChangesOrCancel(ApplicationBundle.message("settings.close.application.unsaved.message"))
    }, frameDisposable)

    // canClose fires before the project-close sequence begins, so the settings window is still
    // visible and the editor context is intact. Must be registered directly (not via message bus)
    // for VetoableProjectManagerListener to be honored. In the IDE-exit path, canExitApplication
    // (above) already cleared isModified, so canClose returns true without showing another dialog.
    val projectManager = ProjectManager.getInstance()
    val closeVetoer = object : VetoableProjectManagerListener {
      override fun canClose(project: Project): Boolean {
        if (project != this@SettingsFrame.project) return true
        return promptUnsavedChangesOrCancel(
          ApplicationBundle.message("settings.close.project.unsaved.message", project.name))
      }
    }
    projectManager.addProjectManagerListener(closeVetoer)
    Disposer.register(frameDisposable) { projectManager.removeProjectManagerListener(closeVetoer) }

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
    null,  // extraHeaderAction: no pin button in legacy project-switcher mode
  )

  // ── Project widget ────────────────────────────────────────────────────────────

  /** Creates [projectWidget] and [projectWidgetPanel] once during initialization. */
  private fun createProjectWidget(): JPanel {
    val iconSize = JBUI.scale(16)
    val customizer = ApplicationManager.getApplication().getService(ProjectWindowCustomizerService::class.java)
    val model = MutableCollectionComboBoxModel(openProjects().toMutableList())
    projectWidget = ComboBoxWithWidePopup(model)
    projectWidget!!.renderer = listCellRenderer<Project?> {
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
    projectWidget!!.addItemListener { e ->
      if (!suppressProjectSwitch && e.stateChange == ItemEvent.SELECTED) {
        val selected = e.item as? Project ?: return@addItemListener
        if (selected != project) switchToProject(selected, null, null)
      }
    }

    projectWidgetPanel = object : JPanel(BorderLayout()) {
      override fun getMinimumSize(): Dimension = Dimension(0, super.getMinimumSize().height)
    }
    projectWidgetPanel!!.background = UIUtil.SIDE_PANEL_BACKGROUND
    projectWidgetPanel!!.border = JBUI.Borders.empty(4, 5, 0, 5)
    projectWidgetPanel!!.add(projectWidget!!, BorderLayout.CENTER)
    return projectWidgetPanel!!
  }

  /**
   * Refreshes the project widget's item list and selection to reflect [newProject].
   * Uses [suppressProjectSwitch] to prevent the [ItemEvent] from triggering a re-entrant switch.
   */
  private fun refreshProjectWidget(newProject: Project) {
    val widget = projectWidget ?: return
    suppressProjectSwitch = true
    try {
      @Suppress("UNCHECKED_CAST")
      (widget.model as MutableCollectionComboBoxModel<Project>).update(openProjects())
      widget.model.selectedItem = newProject
    }
    finally {
      suppressProjectSwitch = false
    }
  }

  // ── Project switching ─────────────────────────────────────────────────────────

  /**
   * Asks the user what to do with unsaved changes, then switches to [newProject].
   */
  private fun switchToProject(newProject: Project, toSelect: Configurable?, filter: String?) {
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
        projectWidget?.let { refreshProjectWidget(project) }
        return
      }
      if (choice == Messages.YES) {
        if (!editor.apply()) {
          projectWidget?.let { refreshProjectWidget(project) }
          return // validation failed – stay on current project
        }
        SaveAndSyncHandler.getInstance().scheduleSave(SaveAndSyncHandler.SaveTask(null, true))
      }
      // Messages.NO → discard, fall through
    }
    doSwitchProject(newProject, toSelect, filter)
  }

  /**
   * Shows Apply / Don't Save / Cancel when there are unsaved settings.
   * Returns true if it is safe to proceed (changes applied or discarded),
   * false if the user canceled.
   */
  private fun promptUnsavedChangesOrCancel(@NlsContexts.DialogMessage message: String): Boolean {
    if (!editor.isModified) return true
    return when (Messages.showYesNoCancelDialog(
      getFrame(),
      message,
      ApplicationBundle.message("settings.switch.project.unsaved.title"),
      ApplicationBundle.message("settings.switch.project.button.apply"),
      ApplicationBundle.message("settings.switch.project.button.dont.save"),
      CommonBundle.getCancelButtonText(),
      Messages.getWarningIcon(),
    )) {
      Messages.YES -> {
        editor.apply()
        SaveAndSyncHandler.getInstance().scheduleSave(SaveAndSyncHandler.SaveTask(null, true))
        true
      }
      Messages.NO -> { editor.cancel(null); true }
      else -> false
    }
  }

  /** Unconditionally replaces the editor with one for [newProject]. */
  private fun doSwitchProject(newProject: Project, toSelect: Configurable?, filter: String?) {
    project = newProject
    refreshProjectWidget(newProject)

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

    editor = makeEditor(editorDisposable, newProject, groups, resolvedToSelect, filter)

    contentArea.add(editor, BorderLayout.CENTER)
    projectWidgetPanel?.let { editor.setTreeTopComponent(it) }
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
    onShow()
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
          afterApply()
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
          if (editor.cancel(e)) {
            afterCancel()
            close()
          }
        }
      }, null)

    val applyAction = editor.getApplyAction()
    if (applyAction != null) {
      applyButton = DialogWrapper.createJButtonForAction(applyAction, null).also {
        DialogUtil.registerMnemonic(it)
        it.addActionListener {
          SaveAndSyncHandler.getInstance().scheduleSave(SaveAndSyncHandler.SaveTask(null, true))
        }
      }
    }

    val rightButtons = mutableListOf<JButton>()
    if (OS.CURRENT == OS.macOS) {
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

/** Returns true if this window's ownership chain passes through [ancestor]. */
private fun Window?.isOwnedBy(ancestor: Window): Boolean {
  var w = this?.owner
  while (w != null) {
    if (w === ancestor) return true
    w = w.owner
  }
  return false
}

