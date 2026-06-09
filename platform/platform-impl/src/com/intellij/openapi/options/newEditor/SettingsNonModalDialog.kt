// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.options.newEditor

import com.intellij.CommonBundle
import com.intellij.diagnostic.LoadingState
import com.intellij.ide.HelpTooltip
import com.intellij.ide.SaveAndSyncHandler
import com.intellij.ide.plugins.PluginManagerConfigurable
import com.intellij.ide.plugins.newui.EventHandler
import com.intellij.openapi.MnemonicHelper
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.application.ApplicationListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.help.HelpManager
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurableGroup
import com.intellij.openapi.options.ex.ConfigurableWrapper
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectCloseListener
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.VetoableProjectManagerListener
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.NonModalWindowWrapper
import com.intellij.openapi.ui.OnePixelDivider
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.SearchTextField
import com.intellij.ui.border.CustomLineBorder
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.openapi.util.SystemInfo
import com.intellij.ui.mac.touchbar.Touchbar
import com.intellij.util.ui.DialogUtil
import com.intellij.util.ui.GridBag
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.ApiStatus
import java.awt.AWTEvent
import java.awt.BorderLayout
import java.awt.EventQueue
import java.awt.GridBagLayout
import java.awt.event.ActionEvent
import java.awt.event.WindowEvent
import javax.swing.AbstractAction
import javax.swing.Action
import javax.swing.Box
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JRootPane
import javax.swing.RootPaneContainer
import javax.swing.UIManager
import javax.swing.border.CompoundBorder

/**
 * Non-modal settings window.
 *
 * Supports two modes, toggled via the pin button in the history toolbar:
 * - **Float mode** (pin ON): a non-modal [javax.swing.JDialog] owned by the IDE frame. Stays above the IDE
 *   via Java's window-ownership chain — no `isAlwaysOnTop` required, works on Linux, and modal
 *   dialogs (e.g., "New Project") appear above it naturally. No OS minimize button.
 * - **Window mode** (pin OFF): an independent [javax.swing.JFrame]. Has an OS minimize button; can go behind
 *   the IDE when the IDE is clicked.
 *
 * Switching modes disposes the old AWT window and creates a new window of the other type, moving the shared
 * [mainPanel] (editor + buttons) into it.
 *
 * One instance per application; opening settings from a different project prompts the user to save
 * or discard, closes the current window, and reopens for the new project.
 *
 * Subclasses may override [onShown] (from [NonModalWindowWrapper]), [afterApply], and [afterCancel]
 * to hook into the window lifecycle. Use [SettingsNonModalDialogFactory] to provide a custom subclass.
 */
@ApiStatus.Internal
open class SettingsNonModalDialog @ApiStatus.Internal constructor(
  project: Project,
  groups: List<ConfigurableGroup>,
  configurable: Configurable?,
  filter: String?,
) : NonModalWindowWrapper(project, FLOAT_MODE_KEY, DIMENSION_KEY) {

  companion object {
    private val LOG = logger<SettingsNonModalDialog>()

    /** Stored preference key: `true` = Float mode, `false` = Window mode. Default: Float. */
    private const val FLOAT_MODE_KEY = "ide.settings.window.float"

    /** Window state service key for persisting size and position. Shared with [SettingsDialog] */
    private const val DIMENSION_KEY = "SettingsEditor"

    @Volatile
    private var ourInstance: SettingsNonModalDialog? = null

    private val spotlightPainterFactory = object : SpotlightPainterFactory {
      override fun createSpotlightPainter(
        project: Project, target: JComponent, parent: com.intellij.openapi.Disposable, updater: (SpotlightPainter) -> Unit,
      ): SpotlightPainter = SpotlightPainter(target, updater)
    }

    /**
     * Returns the single app-wide settings window, creating it if necessary.
     * - Same project: navigates to [configurable] / applies [filter]; returns the existing window.
     * - Different project: prompts the user to save/discard, closes the current window, and creates
     *   a new one. If the user canceled or apply failed, returns the existing window unchanged.
     * - No existing window: creates a new one using [create] and returns it.
     *
     * The caller must call [show] on the returned dialog.
     *
     * @param create factory function used to create the dialog; defaults to [SettingsNonModalDialog] constructor.
     *               Pass a custom factory from [SettingsNonModalDialogFactory] to get a subclass.
     */
    @JvmStatic
    fun getOrCreate(
      project: Project,
      groups: List<ConfigurableGroup>,
      configurable: Configurable?,
      filter: String?,
      create: (Project, List<ConfigurableGroup>, Configurable?, String?) -> SettingsNonModalDialog = ::SettingsNonModalDialog,
    ): SettingsNonModalDialog {
      val existing = ourInstance
      if (existing != null && !existing.isDisposed) {
        if (existing.project == project) {
          if (configurable != null) {
            existing.editor.selectWithFilter(configurable, filter)
          }
          else {
            filter?.let { existing.editor.setFilter(it) }
          }
          return existing
        }
        else {
          return existing.handleDifferentProject(project, groups, configurable, filter, create)
        }
      }
      return create(project, groups, configurable, filter).also { ourInstance = it }
    }
  }

  private val editor: SettingsEditor
  private val mainPanel: JPanel

  // ── Initialization ────────────────────────────────────────────────────────────

  init {
    setupLifecycleSubscriptions()

    editor = SettingsEditor(
      frameDisposable, project, groups, configurable, filter,
      true,  // useLeaveState: auto-reset unmodified configurables when navigating back
      ISettingsTreeViewFactory { f, g -> SettingsTreeView(f, g) },
      spotlightPainterFactory,
      createModeAction(),
    )

    mainPanel = JPanel(BorderLayout())
    mainPanel.add(editor, BorderLayout.CENTER)

    val initialSize = editor.getDialogInitialSize()?.takeIf { it.width > 0 && it.height > 0 }
                      ?: JBUI.size(900, 700)
    // activeWindow is set by initWindow(); createButtonPanel() accesses it for the default button.
    // Adding the button panel to mainPanel after initWindow() is fine — Swing re-lays out on show().
    initWindow(mainPanel, JBUI.size(900, 700), initialSize)
    mainPanel.add(createButtonPanel(), BorderLayout.SOUTH)
    MnemonicHelper.init(mainPanel)
  }

  // ── NonModalWindowWrapper overrides ──────────────────────────────────────────

  override fun getWindowTitle(): String = "${CommonBundle.settingsTitle()} \u2013 ${project.name}"

  override fun getAccessibleWindowName(): String = CommonBundle.settingsTitle()

  override fun getPreferredFocusComponent(): JComponent? =
    editor.getPreferredFocusedComponent() ?: mainPanel

  override fun onWindowDeactivated(): Unit = editor.recordWindowLeaveState()

  override fun onWindowActivated(): Unit = editor.resetUnmodifiedOnWindowFocus()

  override fun onWindowClosing(e: WindowEvent) {
    if (editor.cancel(e)) {
      afterCancel()
      close()
    }
  }

  override fun canClose(e: AWTEvent): Boolean {
    val result = editor.cancel(e)
    if (result) afterCancel()
    return result
  }

  override fun installAdditionalShortcuts(rootPane: JRootPane) {
    // Cmd/Ctrl+F: open the settings search field.
    EventHandler.getShortcuts(IdeActions.ACTION_FIND)?.let { shortcut ->
      SearchTextField.FindAction().registerCustomShortcutSet(shortcut, rootPane, frameDisposable)
    }
  }

  override fun uiDataSnapshot(sink: DataSink) {
    super.uiDataSnapshot(sink)
    sink.uiDataSnapshot(editor)
  }

  override fun dispose() {
    if (ourInstance === this) ourInstance = null
    super.dispose()
  }

  // ── Lifecycle hooks for subclasses ────────────────────────────────────────────

  /** Called after settings are successfully applied and before the window closes. Override to react to apply. */
  protected open fun afterApply() {}

  /** Called when settings are canceled (Cancel button, ESC, or the X button). Override to react to cancel. */
  protected open fun afterCancel() {}

  // ── Lifecycle subscriptions ───────────────────────────────────────────────────

  private fun setupLifecycleSubscriptions() {
    val bus = ApplicationManager.getApplication().messageBus

    // Close window when its project closes.
    bus.connect(frameDisposable).subscribe(ProjectCloseListener.TOPIC, object : ProjectCloseListener {
      override fun projectClosed(project: Project) {
        if (project == this@SettingsNonModalDialog.project) close()
      }
    })

    // Veto project close if there are unsaved settings changes.
    val pm = ProjectManager.getInstance()
    val vetoer = object : VetoableProjectManagerListener {
      override fun canClose(p: Project): Boolean {
        if (p != project) return true
        return promptUnsavedChangesOrCancel(
          ApplicationBundle.message("settings.close.project.unsaved.message", p.name))
      }
    }
    pm.addProjectManagerListener(vetoer)
    Disposer.register(frameDisposable) { pm.removeProjectManagerListener(vetoer) }

    // Veto IDE exit / restart if there are unsaved settings changes.
    // canRestartApplication defaults to canExitApplication, and the plugin-only filter
    // lives inside promptUnsavedChangesOrCancel, so both restart and exit are covered.
    ApplicationManagerEx.getApplicationEx().addApplicationListener(object : ApplicationListener {
      override fun canExitApplication(): Boolean {
        return promptUnsavedChangesOrCancel(ApplicationBundle.message("settings.close.application.unsaved.message"))
      }
    }, frameDisposable)
  }

  // ── Unsaved-changes resolution ────────────────────────────────────────────────

  /** Outcome of [resolveUnsavedChanges]. */
  private enum class UnsavedChangesResult {
    /** No unsaved changes at all (includes plugin-only modifications, which are cleaned up internally). */
    NOT_MODIFIED,
    /** The user chose Apply and changes were saved successfully. */
    APPLIED,
    /** The user chose Don't Save; changes were discarded via [SettingsEditor.cancel]. */
    DISCARDED,
    /** Apply was requested but failed (e.g., validation error). */
    APPLY_FAILED,
    /** The user chose Cancel — the caller should abort. */
    CANCELED,
  }

  /**
   * Checks for unsaved changes and, when meaningful changes exist, shows an
   * Apply / Don't Save / Cancel dialog. Plugin-only modifications are considered
   * not meaningful because plugin state is managed by its own session.
   *
   * @return the user's choice as an [UnsavedChangesResult].
   */
  private fun resolveUnsavedChanges(@NlsContexts.DialogMessage message: String): UnsavedChangesResult {
    if (!editor.isModified) return UnsavedChangesResult.NOT_MODIFIED

    val hasNonPluginChanges = editor.modifiedConfigurables.any {
      ConfigurableWrapper.cast(PluginManagerConfigurable::class.java, it) == null
    }
    if (!hasNonPluginChanges) {
      LOG.info("resolveUnsavedChanges: only PluginManagerConfigurable modified, skipping prompt")
      editor.cancel(null)
      return UnsavedChangesResult.NOT_MODIFIED
    }

    return when (Messages.showYesNoCancelDialog(
      activeWindow,
      message,
      ApplicationBundle.message("settings.switch.project.unsaved.title"),
      ApplicationBundle.message("settings.switch.project.button.apply"),
      ApplicationBundle.message("settings.switch.project.button.dont.save"),
      CommonBundle.getCancelButtonText(),
      Messages.getWarningIcon(),
    )) {
      Messages.YES -> {
        if (!editor.apply()) return UnsavedChangesResult.APPLY_FAILED
        SaveAndSyncHandler.getInstance().scheduleSave(SaveAndSyncHandler.SaveTask(null, true))
        UnsavedChangesResult.APPLIED
      }
      Messages.NO -> {
        editor.cancel(null)
        UnsavedChangesResult.DISCARDED
      }
      else -> UnsavedChangesResult.CANCELED
    }
  }

  // ── Different-project handling ────────────────────────────────────────────────

  /**
   * Prompts the user to save or discard changes, closes this window, and creates
   * a new window for [newProject] via [create].
   *
   * If the user canceled or apply failed, returns this (existing) dialog unchanged.
   */
  private fun handleDifferentProject(
    newProject: Project,
    groups: List<ConfigurableGroup>,
    toSelect: Configurable?,
    filter: String?,
    create: (Project, List<ConfigurableGroup>, Configurable?, String?) -> SettingsNonModalDialog,
  ): SettingsNonModalDialog {
    val result = resolveUnsavedChanges(
      ApplicationBundle.message("settings.switch.project.unsaved.message", project.name, newProject.name))
    if (result == UnsavedChangesResult.CANCELED || result == UnsavedChangesResult.APPLY_FAILED) return this

    close()
    return create(newProject, groups, toSelect, filter).also { ourInstance = it }
  }

  /**
   * Shows Apply / Don't Save / Cancel when there are unsaved settings.
   * Returns true if safe to proceed (changes applied or discarded), false if the user canceled.
   */
  private fun promptUnsavedChangesOrCancel(@NlsContexts.DialogMessage message: String): Boolean {
    return when (resolveUnsavedChanges(message)) {
      UnsavedChangesResult.NOT_MODIFIED,
      UnsavedChangesResult.APPLIED,
      UnsavedChangesResult.DISCARDED -> true
      UnsavedChangesResult.APPLY_FAILED,
      UnsavedChangesResult.CANCELED -> {
        // After Cancel the IDE-close veto returns; on Windows the IDE main frame reclaims
        // focus, hiding the independent Settings JFrame (Window mode). Restore it on top.
        EventQueue.invokeLater {
          activeWindow.toFront()
          activeWindow.requestFocus()
        }
        false
      }
    }
  }

  // ── Button panel ──────────────────────────────────────────────────────────────

  private fun createHelpButton(): JButton {
    val helpButton = JButton(object : AbstractAction() {
      override fun actionPerformed(e: ActionEvent) {
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

    val okAction = object : AbstractAction(CommonBundle.getOkButtonText()) {
      override fun actionPerformed(e: ActionEvent) {
        UIUtil.stopFocusedEditing(activeWindow)
        if (editor.apply()) {
          SaveAndSyncHandler.getInstance().scheduleSave(SaveAndSyncHandler.SaveTask(null, true))
          afterApply()
          close()
        }
      }
    }
    okAction.putValue(DialogWrapper.DEFAULT_ACTION, true)

    val okButton = DialogWrapper.createJButtonForAction(okAction,
                                                        (activeWindow as? RootPaneContainer)?.rootPane)

    val cancelButton = DialogWrapper.createJButtonForAction(
      object : AbstractAction(CommonBundle.getCancelButtonText()) {
        override fun actionPerformed(e: ActionEvent) {
          if (editor.cancel(e)) {
            afterCancel()
            close()
          }
        }
      }, null)

    val rawApplyAction = editor.getApplyAction()
    val applyButton = if (rawApplyAction != null) {
      val wrappedApply = object : AbstractAction() {
        init {
          putValue(Action.NAME, rawApplyAction.getValue(Action.NAME))
          isEnabled = rawApplyAction.isEnabled
          rawApplyAction.addPropertyChangeListener { evt ->
            if (evt.propertyName == "enabled") {
              isEnabled = rawApplyAction.isEnabled
            }
            else {
              putValue(evt.propertyName, rawApplyAction.getValue(evt.propertyName))
            }
          }
        }
        override fun actionPerformed(e: ActionEvent) {
          rawApplyAction.actionPerformed(e)
          SaveAndSyncHandler.getInstance().scheduleSave(SaveAndSyncHandler.SaveTask(null, true))
        }
      }
      DialogWrapper.createJButtonForAction(wrappedApply, null).also {
        DialogUtil.registerMnemonic(it)
      }
    }
    else null

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
