// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.log

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.ExtensionPointListener
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task.Backgroundable
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.WindowWrapper
import com.intellij.openapi.ui.WindowWrapperBuilder
import com.intellij.openapi.util.Comparing
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsRoot
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager.Companion.getInstance
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.content.ContentManager
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.vcs.log.VcsLogBundle
import com.intellij.vcs.log.VcsLogProvider
import com.intellij.vcs.log.impl.VcsLogContentUtil
import com.intellij.vcs.log.impl.VcsLogManager
import com.intellij.vcs.log.impl.VcsLogTabLocation
import com.intellij.vcs.log.impl.VcsProjectLog
import com.intellij.vcs.log.ui.VcsLogPanel
import com.intellij.vcs.log.ui.VcsLogUiEx
import com.intellij.vcs.log.visible.filters.VcsLogFilterObject.collection
import com.intellij.vcs.log.visible.filters.VcsLogFilterObject.fromRoots
import git4idea.GitUtil
import git4idea.GitVcs
import git4idea.config.GitExecutableManager
import git4idea.i18n.GitBundle
import git4idea.repo.GitRepositoryImpl
import git4idea.repo.GitRepositoryManager
import org.jetbrains.annotations.Nls
import java.awt.BorderLayout
import java.io.File
import javax.swing.JComponent
import javax.swing.JPanel

class GitShowExternalLogAction : DumbAwareAction() {
  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.isEnabledAndVisible = e.project != null
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.getRequiredData(CommonDataKeys.PROJECT)
    val vcs = GitVcs.getInstance(project)
    val roots = getGitRootsFromUser(project)
    if (roots.isEmpty()) {
      return
    }
    val window = getInstance(project).getToolWindow(ChangesViewContentManager.TOOLWINDOW_ID)
    if (project.isDefault || !ProjectLevelVcsManager.getInstance(project).hasActiveVcss() || window == null) {
      ProgressManager.getInstance().run(ShowLogTask(project, roots, vcs, true) { disposable ->
        val content = createContent(this, getMainLogUiFactory(calcLogId(roots), null), roots, false, disposable)
        showLogContentWindow(project, content, GitBundle.message("git.log.external.window.title"), disposable)
      })
    }
    else {
      val description = GitBundle.message("git.log.external.tab.description", roots.joinToString("\n") { obj: VirtualFile -> obj.path })
      showExternalGitLogInToolwindow(project, window, vcs, roots, calcTabName(window.contentManager, roots), description)
    }
  }
}

fun showExternalGitLogInToolwindow(project: Project,
                                   toolWindow: ToolWindow,
                                   vcs: GitVcs,
                                   roots: List<VirtualFile>,
                                   tabTitle: @NlsContexts.TabTitle String,
                                   tabDescription: @NlsContexts.Tooltip String) {
  showExternalGitLogInToolwindow(project, toolWindow, { it.getMainLogUiFactory(calcLogId(roots), null) }, vcs, roots, tabTitle, tabDescription)
}

fun <T : VcsLogUiEx> showExternalGitLogInToolwindow(project: Project,
                                                    toolWindow: ToolWindow,
                                                    uiFactory: (VcsLogManager) -> VcsLogManager.VcsLogUiFactory<T>,
                                                    vcs: GitVcs,
                                                    roots: List<VirtualFile>,
                                                    tabTitle: @NlsContexts.TabTitle String,
                                                    tabDescription: @NlsContexts.Tooltip String) {
  val showContent = {
    if (!selectProjectLog(project, vcs, roots) && !selectAlreadyOpened(toolWindow.contentManager, roots)) {
      ProgressManager.getInstance().run(ShowLogTask(project, roots, vcs, false) { disposable ->
        val isToolWindowTab = toolWindow.id == ChangesViewContentManager.TOOLWINDOW_ID
        val component = createContent(this, uiFactory(this), roots, isToolWindowTab, disposable)
        toolWindow.addLogContent(project, component, tabTitle, tabDescription, disposable)
      })
    }
  }
  if (!toolWindow.isVisible) {
    toolWindow.activate(showContent, true)
  }
  else {
    showContent()
  }
}

private class ShowLogTask(project: Project,
                          private val roots: List<VirtualFile>,
                          private val vcs: GitVcs,
                          private val testGitExecutable: Boolean,
                          private val showLog: VcsLogManager.(Disposable) -> Unit) :
  Backgroundable(project, @Suppress("DialogTitleCapitalization") GitBundle.message("git.log.external.loading.process"), true) {

  private val disposable = Disposer.newDisposable()

  @Volatile
  private lateinit var manager: VcsLogManager

  override fun run(indicator: ProgressIndicator) {
    if (testGitExecutable && !GitExecutableManager.getInstance().testGitExecutableVersionValid(project)) {
      throw ProcessCanceledException()
    }
    manager = createLogManager(project, vcs, roots, disposable)
  }

  override fun onSuccess() {
    if (!project.isDisposed) {
      manager.showLog(disposable)
    }
  }
}

private fun ToolWindow.addLogContent(project: Project,
                                     component: JComponent,
                                     tabTitle: @NlsContexts.TabTitle String,
                                     tabDescription: @NlsContexts.Tooltip String,
                                     disposable: Disposable) {
  val content = ContentFactory.getInstance().createContent(component, tabTitle, false)
  content.setDisposer(disposable)
  content.description = tabDescription
  content.isCloseable = true

  val cm = contentManager
  cm.addContent(content)
  cm.setSelectedContent(content)

  doOnProviderRemoval(project, disposable) { cm.removeContent(content, true) }
}

private fun showLogContentWindow(project: Project, content: JComponent, @NlsContexts.DialogTitle title: String, disposable: Disposable) {
  val window = WindowWrapperBuilder(WindowWrapper.Mode.FRAME, content)
    .setProject(project)
    .setTitle(title)
    .setPreferredFocusedComponent(content)
    .setDimensionServiceKey(GitShowExternalLogAction::class.java.name)
    .build()
  Disposer.register(window, disposable)
  doOnProviderRemoval(project, disposable) { window.close() }
  window.show()
}

@RequiresEdt
private fun <T : VcsLogUiEx> createContent(manager: VcsLogManager,
                                           uiFactory: VcsLogManager.VcsLogUiFactory<T>,
                                           roots: List<VirtualFile>,
                                           isToolWindowTab: Boolean,
                                           disposable: Disposable): MyContentComponent {
  val ui = manager.createLogUi(uiFactory, if (isToolWindowTab) VcsLogTabLocation.TOOL_WINDOW else VcsLogTabLocation.STANDALONE)
  Disposer.register(disposable, ui)
  return MyContentComponent(VcsLogPanel(manager, ui), roots)
}

@RequiresBackgroundThread
private fun createLogManager(project: Project,
                             vcs: GitVcs,
                             roots: List<VirtualFile>,
                             disposable: Disposable): VcsLogManager {
  val repositoryManager = GitRepositoryManager.getInstance(project)
  for (root in roots) {
    repositoryManager.addExternalRepository(root, GitRepositoryImpl.createInstance(root, project, disposable))
  }
  val manager = VcsLogManager(project, ApplicationManager.getApplication().getService(GitExternalLogTabsProperties::class.java),
                              roots.map { VcsRoot(vcs, it) })
  Disposer.register(disposable, Disposable { manager.dispose { roots.forEach { repositoryManager.removeExternalRepository(it) } } })
  return manager
}

private class MyContentComponent(actualComponent: JComponent, val roots: Collection<VirtualFile>) : JPanel(BorderLayout()) {
  init {
    add(actualComponent)
  }
}

private const val EXTERNAL = "EXTERNAL"

private fun calcLogId(roots: List<VirtualFile>): String {
  return "$EXTERNAL " + roots.joinToString(File.pathSeparator) { obj: VirtualFile -> obj.path }
}

@Nls
private fun calcTabName(cm: ContentManager, roots: List<VirtualFile>): String {
  val name = VcsLogBundle.message("vcs.log.tab.name") + " (" + roots.first().name + (if (roots.size > 1) "+" else "") + ")"
  var candidate = name
  var cnt = 1
  while (cm.contents.any { content: Content -> content.displayName == candidate }) {
    candidate = "$name-$cnt"
    cnt++
  }
  return candidate
}

private fun getGitRootsFromUser(project: Project): List<VirtualFile> {
  val descriptor = FileChooserDescriptor(false, true, false, true, false, true)
  val virtualFiles = FileChooser.chooseFiles(descriptor, project, null)
  return virtualFiles.filter { GitUtil.isGitRoot(it.toNioPath()) }
}

private fun selectProjectLog(project: Project,
                             vcs: GitVcs,
                             requestedRoots: List<VirtualFile>): Boolean {
  val projectRoots = listOf(*ProjectLevelVcsManager.getInstance(project).getRootsUnderVcs(vcs))
  if (!projectRoots.containsAll(requestedRoots)) return false

  if (requestedRoots.containsAll(projectRoots)) return VcsLogContentUtil.selectMainLog(project)
  val filters = collection(fromRoots(requestedRoots))
  return VcsProjectLog.getInstance(project).openLogTab(filters) != null
}

private fun selectAlreadyOpened(cm: ContentManager, roots: Collection<VirtualFile>): Boolean {
  val content = cm.contents.firstOrNull { content ->
    val component = content.component
    if (component is MyContentComponent) {
      Comparing.haveEqualElements(roots, component.roots)
    }
    else {
      false
    }
  } ?: return false
  cm.setSelectedContent(content)
  return true
}

private fun doOnProviderRemoval(project: Project, disposable: Disposable, closeTab: () -> Unit) {
  VcsLogProvider.LOG_PROVIDER_EP.getPoint(project).addExtensionPointListener(object : ExtensionPointListener<VcsLogProvider> {
    override fun extensionRemoved(extension: VcsLogProvider, pluginDescriptor: PluginDescriptor) {
      if (extension.supportedVcs == GitVcs.getKey()) {
        closeTab()
      }
    }
  }, false, disposable)
}
