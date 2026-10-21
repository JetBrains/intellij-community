// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.options.newEditor

import com.intellij.CommonBundle
import com.intellij.ide.ActivityTracker
import com.intellij.ide.SaveAndSyncHandler
import com.intellij.ide.plugins.PluginManagerConfigurable
import com.intellij.ide.plugins.newui.EventHandler
import com.intellij.ide.startup.StartupManagerEx
import com.intellij.ide.ui.laf.darcula.ui.DarculaButtonUI
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.application.ApplicationListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.WriteIntentReadAction
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerKeys
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileEditor.FileEditorOpenRequest
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurableGroup
import com.intellij.openapi.options.ex.ConfigurableExtensionPointUtil
import com.intellij.openapi.options.ex.ConfigurableVisitor
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.VetoableProjectManagerListener
import com.intellij.openapi.startup.StartupManager
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.registry.RegistryManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.ui.components.JBLoadingPanel
import com.intellij.ui.components.panels.Wrapper
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.launchOnShow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.resolvedPromise
import java.awt.AWTEvent
import java.awt.BorderLayout
import java.awt.Toolkit
import java.awt.Window
import java.awt.event.AWTEventListener
import java.awt.event.WindowEvent
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.SwingUtilities
import kotlin.time.Duration.Companion.seconds

/**
 * Shows the Settings of a project in the Settings tool window and in the Settings editor tab.
 *
 * Both show one [SettingsEditor] in the detached mode. The tool window shows the search field and the tree, and the tab shows the page.
 * The editor lives as long as the project, so the tree, the selection, and the history stay when the tab closes.
 * A change waits for Apply, as in the Settings dialog. Closing the tab, the project, or the IDE asks about the changes that wait.
 */
@Service(Service.Level.PROJECT)
internal class SettingsToolWindowManager(private val project: Project, private val coroutineScope: CoroutineScope) : Disposable {
  companion object {
    fun getInstance(project: Project): SettingsToolWindowManager = project.service()

    /** Whether the Settings of [project] open here instead of a non-modal dialog. */
    @RequiresEdt
    fun isEnabled(project: Project): Boolean {
      return !project.isDefault &&
             RegistryManager.getInstance().`is`(SETTINGS_TOOL_WINDOW_REGISTRY_KEY) &&
             ToolWindowManager.getInstance(project).getToolWindow(SETTINGS_TOOL_WINDOW_ID) != null
    }

    /**
     * Shows the Settings of [project] when they have a tree already, and returns `true`.
     * Returns `false` when the tree is not built yet, so that the caller builds it only then.
     */
    @RequiresEdt
    fun showIfCreated(project: Project): Boolean {
      if (!isEnabled(project)) {
        return false
      }
      return project.serviceIfCreated<SettingsToolWindowManager>()?.showIfCreated(filter = null) { null } == true
    }

    /** Returns the manager whose Settings tab shows [file]. */
    fun findByFile(file: SettingsVirtualFile): SettingsToolWindowManager? {
      return ProjectManager.getInstance().openProjects.firstNotNullOfOrNull { project ->
        project.serviceIfCreated<SettingsToolWindowManager>()?.takeIf { it.file == file }
      }
    }
  }

  /** The file of the Settings editor tab. */
  val file: SettingsVirtualFile = SettingsVirtualFile()

  /** The content of the Settings tool window. It shows a loading panel until the tree is built. */
  val sidebar: Wrapper = Wrapper(JBLoadingPanel(BorderLayout(), this).apply { startLoading() })

  /** The content of the Settings editor tab. */
  val content: Wrapper = Wrapper()

  private var editor: SettingsEditor? = null
  private var groups: List<ConfigurableGroup> = emptyList()
  private var editorJob: Job? = null

  /** Whether a page has a change that waits for Apply. */
  val isModified: Boolean
    get() = editor?.isModified == true

  init {
    subscribeToEvents()
    trackWindowActivation()
    EventHandler.getShortcuts(IdeActions.ACTION_FIND)?.let { shortcuts ->
      DumbAwareAction.create { focusSearch() }.registerCustomShortcutSet(shortcuts, content, this)
    }
  }

  /**
   * Shows the Settings: the page of [toSelect] in the Settings tab, and the tree in the tool window.
   *
   * [groups] is called only when the tree is not built yet. [toSelect] receives the groups of the tree,
   * because the tree selects a node by the [Configurable] instance.
   */
  @RequiresEdt
  fun show(groups: () -> List<ConfigurableGroup>, toSelect: (List<ConfigurableGroup>) -> Configurable?, filter: String?) {
    if (showIfCreated(filter, toSelect)) {
      return
    }
    editorJob?.cancel()
    val newGroups = groups()
    val configurable = toSelect(newGroups)
    createEditor(newGroups, configurable, filter)
    reveal(configurable)
  }

  @RequiresEdt
  private fun showIfCreated(filter: String?, toSelect: (List<ConfigurableGroup>) -> Configurable?): Boolean {
    val editor = editor ?: return false
    val configurable = toSelect(groups)
    if (configurable != null) {
      editor.selectWithFilter(configurable, filter)
    }
    else if (filter != null) {
      editor.setFilter(filter)
    }
    reveal(configurable)
    return true
  }

  /** Builds the tree in the background when the tool window opens before any call that shows the Settings. */
  @RequiresEdt
  fun createEditorInBackground() {
    if (editor != null || editorJob?.isActive == true) {
      return
    }
    editorJob = coroutineScope.launch {
      (project.serviceAsync<StartupManager>() as StartupManagerEx).waitForInitProjectActivities(null)
      // the filter reads `configurables` of every group, so it stays on the background thread
      val groups = listOf(ConfigurableExtensionPointUtil.getConfigurableGroup(project, true)).filter { it.configurables.isNotEmpty() }
      withContext(Dispatchers.EDT) {
        if (editor == null) {
          createEditor(groups, configurable = null, filter = null)
        }
      }
    }
  }

  /** The component that gets the focus when the Settings tab opens. */
  fun getPreferredFocusedPageComponent(): JComponent? = editor?.editor?.getPreferredFocusedComponent()

  /** The component that gets the focus when the Settings tool window opens. */
  fun getPreferredFocusedSidebarComponent(): JComponent? = editor?.treeView?.tree

  @VisibleForTesting
  @RequiresEdt
  internal fun createEditor(groups: List<ConfigurableGroup>, configurable: Configurable?, filter: String?): SettingsEditor {
    val editor = SettingsEditor(
      this, project, groups, configurable, filter,
      true,  // useLeaveState: reset an unmodified page that changed while the user was away
      ISettingsTreeViewFactory { f, g -> SettingsTreeView(f, g).also { it.useToolWindowStyle() } },
      spotlightPainterFactory,
      ApplyAction(),
      true,  // detached: the tool window shows the sidebar, and the Settings tab shows the content
    )
    editor.addOptionsListener(object : OptionsEditorColleague.Adapter() {
      override fun onSelected(configurable: Configurable?, oldConfigurable: Configurable?): Promise<in Any> {
        // a click in the tree opens the page, and a background reload of the tree reopens no closed tab
        if (configurable != null && getToolWindow()?.isVisible == true) {
          openTab(focus = false)
        }
        return resolvedPromise()
      }

      override fun onModifiedAdded(configurable: Configurable): Promise<in Any> = modifiedChanged()

      override fun onModifiedRemoved(configurable: Configurable): Promise<in Any> = modifiedChanged()
    })
    this.editor = editor
    this.groups = groups
    sidebar.setContent(editor.sidebar)
    content.setContent(editor.content)
    return editor
  }

  /** Opens the Settings tab, and shows the tree unless the page is Plugins: a user edits plugins as a separate job. */
  @RequiresEdt
  private fun reveal(configurable: Configurable?) {
    val toolWindow = getToolWindow()
    if (configurable == null) {
      // as in the Settings dialog, the focus goes to the tree, so that the user can type a search text at once
      openTab(focus = false)
      toolWindow?.activate(null)
    }
    else {
      openTab(focus = true)
      if (ConfigurableVisitor.getId(configurable) != PluginManagerConfigurable.ID) {
        toolWindow?.show()
      }
    }
  }

  private fun openTab(focus: Boolean) {
    FileEditorManager.getInstance(project).requestOpenFile(file, FileEditorOpenRequest.defaults().withReuseOpen(true).withRequestFocus(focus))
  }

  private fun getToolWindow(): ToolWindow? = ToolWindowManager.getInstance(project).getToolWindow(SETTINGS_TOOL_WINDOW_ID)

  private fun focusSearch() {
    val search = editor?.search ?: return
    getToolWindow()?.activate {
      IdeFocusManager.getInstance(project).requestFocus(search.textEditor, true)
    }
  }

  private fun modifiedChanged(): Promise<in Any> {
    // the toolbar of the Settings tab shows or hides the Apply button with the next update
    ActivityTracker.getInstance().inc()
    for (fileEditor in FileEditorManager.getInstance(project).getAllEditors(file)) {
      (fileEditor as? SettingsFileEditor)?.updateModified()
    }
    return resolvedPromise()
  }

  private fun subscribeToEvents() {
    val connection = project.messageBus.connect(this)
    connection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, object : FileEditorManagerListener {
      override fun selectionChanged(event: FileEditorManagerEvent) {
        val editor = editor ?: return
        if (event.oldFile == file) {
          editor.recordWindowLeaveState()
        }
        if (event.newFile == file) {
          editor.resetUnmodifiedOnWindowFocus()
        }
      }

      override fun fileClosed(source: FileEditorManager, file: VirtualFile) {
        // a tab that moves to another window or splitter closes and opens again
        if (file == this@SettingsToolWindowManager.file && file.getUserData(FileEditorManagerKeys.CLOSING_TO_REOPEN) != true) {
          tabClosed()
        }
      }
    })
    connection.subscribe(ToolWindowManagerListener.TOPIC, object : ToolWindowManagerListener {
      override fun toolWindowShown(toolWindow: ToolWindow) {
        // the tree and the page stay together: a tree without its page has no use
        if (toolWindow.id == SETTINGS_TOOL_WINDOW_ID && editor != null) {
          openTab(focus = false)
        }
      }
    })

    val projectManager = ProjectManager.getInstance()
    val vetoer = VetoableProjectManagerListener { closingProject ->
      closingProject != project ||
      resolveUnsavedChanges(ApplicationBundle.message("settings.close.project.unsaved.message", project.name))
    }
    projectManager.addProjectManagerListener(vetoer)
    Disposer.register(this) { projectManager.removeProjectManagerListener(vetoer) }

    ApplicationManagerEx.getApplicationEx().addApplicationListener(object : ApplicationListener {
      override fun canExitApplication(): Boolean {
        return resolveUnsavedChanges(ApplicationBundle.message("settings.close.application.unsaved.message", project.name))
      }
    }, this)
  }

  /**
   * Keeps the leave state of the page while the Settings tab shows, as [com.intellij.openapi.ui.NonModalWindowWrapper] does
   * for the Settings dialog. The user leaves the page when another frame or another application becomes active.
   * A dialog or a popup of the window does not leave the page, because a change there is a part of the work on the page.
   */
  @OptIn(FlowPreview::class)
  private fun trackWindowActivation() {
    content.launchOnShow("Settings tab window activation") {
      // the collection must go on while a modal dialog shows, and the default modality stops it.
      // A page can take the write-intent lock in reset(), and Dispatchers.UI forbids that lock
      withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
        val window = SwingUtilities.getWindowAncestor(content) ?: return@withContext
        val activeWindow = MutableStateFlow<Window?>(null)
        // a dialog of the window can move the focus to another application, for example to a browser for OAuth
        val deactivatedToChild = AtomicBoolean(false)
        launch {
          val listener = AWTEventListener { event ->
            when (event.id) {
              WindowEvent.WINDOW_ACTIVATED -> {
                val activated = event.source as Window
                if (activated !== window && activated.isSameOrOwnedBy(window)) {
                  deactivatedToChild.set(true)
                }
                activeWindow.value = activated
              }
              WindowEvent.WINDOW_DEACTIVATED -> activeWindow.value = null
            }
          }
          val toolkit = Toolkit.getDefaultToolkit()
          toolkit.addAWTEventListener(listener, AWTEvent.WINDOW_EVENT_MASK)
          try {
            activeWindow.value = if (window.isActive) window else null
            awaitCancellation()
          }
          finally {
            toolkit.removeAWTEventListener(listener)
          }
        }
        activeWindow
          // Wayland gives no opposite window, so only an activation soon after a deactivation shows a switch inside the IDE
          .debounce { if (it == null) 1.seconds else 0.seconds }
          .map { active ->
            if (active != null && active.isSameOrOwnedBy(window)) {
              if (active === window) {
                deactivatedToChild.set(false)
              }
              true
            }
            else {
              deactivatedToChild.get()
            }
          }
          .distinctUntilChanged()
          .collect { isWindowActive ->
            if (isWindowActive) {
              editor?.resetUnmodifiedOnWindowFocus()
            }
            else {
              editor?.recordWindowLeaveState()
            }
          }
      }
    }
  }

  /**
   * Asks about the changes that wait for Apply before the Settings tab closes, see [SettingsTabPreCloseCheck].
   * Returns `false` when the tab must stay open.
   */
  @RequiresEdt
  fun canCloseTab(): Boolean {
    return resolveUnsavedChanges(ApplicationBundle.message("settings.close.tab.unsaved.message", project.name))
  }

  private fun tabClosed() {
    // a close without the pre-close check asks here. No dialog may show inside the closing of a tab, so the question waits
    ApplicationManager.getApplication().invokeLater({
      if (FileEditorManager.getInstance(project).isFileOpen(file)) {
        return@invokeLater
      }
      if (resolveUnsavedChanges(ApplicationBundle.message("settings.close.tab.unsaved.message", project.name))) {
        getToolWindow()?.hide()
      }
      else {
        openTab(focus = true)
      }
    }, project.disposed)
  }

  /**
   * Asks the user to apply or to drop the changes that wait for Apply.
   * Returns `false` when the user cancels, or when a page rejects its values. Then the caller must keep the Settings open.
   */
  @RequiresEdt
  private fun resolveUnsavedChanges(@NlsContexts.DialogMessage message: String): Boolean {
    val editor = editor ?: return true
    stopFocusedEditing()
    // the editor checks a page for changes only after a delay, and a change of the last second must count too
    editor.checkCurrentModified()
    if (!editor.isModified) {
      return true
    }
    // as in the non-modal Settings dialog, the close drops the changes of the Plugins page with no question.
    // A reset keeps the plugin session for the next visit, and a cancel removes it
    if (editor.modifiedConfigurables.all { ConfigurableVisitor.getId(it) == PluginManagerConfigurable.ID }) {
      editor.reset()
      return true
    }
    val answer = MessageDialogBuilder.yesNoCancel(ApplicationBundle.message("settings.switch.project.unsaved.title"), message)
      .yesText(ApplicationBundle.message("settings.switch.project.button.apply"))
      .noText(ApplicationBundle.message("settings.switch.project.button.dont.save"))
      .cancelText(CommonBundle.getCancelButtonText())
      .asWarning()
      .show(project)
    return when (answer) {
      Messages.YES -> apply()
      Messages.NO -> {
        editor.reset()
        true
      }
      else -> false
    }
  }

  /** Applies the changes of every page. Returns `false` when a page rejects its values: then the tree selects that page. */
  @RequiresEdt
  private fun apply(): Boolean {
    val editor = editor ?: return true
    stopFocusedEditing()
    // a click on a Swing button holds no write-intent lock, and some pages apply in a write action
    val applied = WriteIntentReadAction.compute { editor.apply() }
    if (applied) {
      SaveAndSyncHandler.getInstance().scheduleSave(SaveAndSyncHandler.SaveTask(null, true))
    }
    return applied
  }

  /** Gives the value of a table cell that the user still edits to its page. */
  private fun stopFocusedEditing() {
    SwingUtilities.getWindowAncestor(content)?.let { UIUtil.stopFocusedEditing(it) }
  }

  override fun dispose() {
    editorJob?.cancel()
    editor = null
  }

  /** The Apply button in the header of the Settings tab. It shows while a page has a change that waits for Apply. */
  private inner class ApplyAction : DumbAwareAction(CommonBundle.getApplyButtonText()), CustomComponentAction {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun update(e: AnActionEvent) {
      e.presentation.isEnabledAndVisible = isModified
    }

    override fun actionPerformed(e: AnActionEvent) {
      apply()
    }

    override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
      val button = JButton(presentation.text)
      button.putClientProperty(DarculaButtonUI.DEFAULT_STYLE_KEY, true)
      button.addActionListener { apply() }
      return JBUI.Panels.simplePanel(button).withBorder(JBUI.Borders.emptyLeft(8)).andTransparent()
    }

    override fun updateCustomComponent(component: JComponent, presentation: Presentation) {
      component.isVisible = presentation.isVisible
      UIUtil.findComponentOfType(component, JButton::class.java)?.isEnabled = presentation.isEnabled
    }
  }
}

/** Returns `true` when the owner chain of this window holds [ancestor]. The chain starts with this window. */
private fun Window.isSameOrOwnedBy(ancestor: Window): Boolean {
  var window: Window? = this
  while (window != null) {
    if (window === ancestor) {
      return true
    }
    window = window.owner
  }
  return false
}

private val spotlightPainterFactory = object : SpotlightPainterFactory {
  override fun createSpotlightPainter(
    project: Project, target: JComponent, parent: Disposable, updater: (SpotlightPainter) -> Unit,
  ): SpotlightPainter = SpotlightPainter(target, updater)
}
