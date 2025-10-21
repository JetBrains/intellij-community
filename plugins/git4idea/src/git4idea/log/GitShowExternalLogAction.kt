// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.log

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.UI
import com.intellij.openapi.application.UiWithModelAccess
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.extensions.ExtensionPointListener
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.progress.checkCanceled
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
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.content.ContentManager
import com.intellij.vcs.log.VcsLogBundle
import com.intellij.vcs.log.VcsLogProvider
import com.intellij.vcs.log.impl.VcsLogContentUtil
import com.intellij.vcs.log.impl.VcsLogManager
import com.intellij.vcs.log.impl.VcsProjectLog
import com.intellij.vcs.log.ui.VcsLogPanel
import com.intellij.vcs.log.ui.VcsLogUiEx
import com.intellij.vcs.log.util.VcsLogUtil.getProvidersMapText
import com.intellij.vcs.log.visible.filters.VcsLogFilterObject.collection
import com.intellij.vcs.log.visible.filters.VcsLogFilterObject.fromRoots
import git4idea.GitUtil
import git4idea.GitVcs
import git4idea.config.GitExecutableManager
import git4idea.i18n.GitBundle
import git4idea.repo.GitRepositoryImpl
import git4idea.repo.GitRepositoryManager
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.Nls
import java.awt.BorderLayout
import java.io.File
import javax.swing.JComponent
import javax.swing.JPanel
import kotlin.coroutines.cancellation.CancellationException

internal class GitShowExternalLogAction : DumbAwareAction() {
  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.isEnabledAndVisible = e.project != null
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.getData(CommonDataKeys.PROJECT) ?: return
    val roots = getGitRootsFromUser(project)
    if (roots.isEmpty()) {
      return
    }
    val window = ToolWindowManager.getInstance(project).getToolWindow(ChangesViewContentManager.TOOLWINDOW_ID)
    if (project.isDefault || !ProjectLevelVcsManager.getInstance(project).hasActiveVcss() || window == null) {
      showExternalGitLogInWindow(project, roots)
    }
    else {
      val description = GitBundle.message("git.log.external.tab.description", roots.joinToString("\n") { obj: VirtualFile -> obj.path })
      showExternalGitLogInToolwindow(project, window, roots, calcTabName(window.contentManager, roots), description)
    }
  }
}

@Internal
fun showExternalGitLogInToolwindow(
  project: Project,
  toolWindow: ToolWindow,
  roots: List<VirtualFile>,
  tabTitle: @NlsContexts.TabTitle String,
  tabDescription: @NlsContexts.Tooltip String,
) {
  showExternalGitLogInToolwindow(project, toolWindow, {
    createLogUi(calcLogId(roots), null)
  }, roots, tabTitle, tabDescription)
}

@Internal
fun <T : VcsLogUiEx> showExternalGitLogInToolwindow(
  project: Project,
  toolWindow: ToolWindow,
  uiFactory: VcsLogManager.() -> T,
  roots: List<VirtualFile>,
  tabTitle: @NlsContexts.TabTitle String,
  tabDescription: @NlsContexts.Tooltip String,
) {
  val showContent = {
    if (!selectProjectLog(project, roots) && !selectAlreadyOpened(toolWindow.contentManager, roots)) {
      showExternalGitLogInWindow(project, roots, uiFactory, toolWindow, tabTitle, tabDescription)
    }
  }
  if (!toolWindow.isVisible) {
    toolWindow.activate(showContent, true)
  }
  else {
    showContent()
  }
}

private fun <T : VcsLogUiEx> showExternalGitLogInWindow(
  project: Project,
  roots: List<VirtualFile>,
  uiFactory: VcsLogManager.() -> T,
  toolWindow: ToolWindow,
  tabTitle: @NlsContexts.TabTitle String,
  tabDescription: @NlsContexts.Tooltip String,
) {
  project.service<GitExternalLogService>().showLog(roots, false) {
    val ui = uiFactory(this)
    val component = MyContentComponent(VcsLogPanel(this, ui), roots)
    toolWindow.addLogContent(project, component, tabTitle, tabDescription, ui)
    ui
  }
}

private fun showExternalGitLogInWindow(
  project: Project,
  roots: List<VirtualFile>,
) {
  project.service<GitExternalLogService>().showLog(roots, true) {
    val ui = createLogUi(calcLogId(roots), null)
    val content = MyContentComponent(VcsLogPanel(this, ui), roots)
    showLogContentWindow(project, content, GitBundle.message("git.log.external.window.title"), ui)
    ui
  }
}

@Service(Service.Level.PROJECT)
private class GitExternalLogService(
  private val project: Project,
  private val cs: CoroutineScope,
) {
  fun showLog(roots: List<VirtualFile>, testGitExecutable: Boolean, showLog: VcsLogManager.() -> VcsLogUiEx) {
    cs.launch(Dispatchers.Default + CoroutineName("External Git Log for roots $roots")) {
      val cs = this
      val repositoriesDisposable = Disposer.newDisposable()
      val repositoryManager = project.serviceAsync<GitRepositoryManager>()

      @Suppress("DialogTitleCapitalization")
      val title = GitBundle.message("git.log.external.loading.process")
      val manager = withBackgroundProgress(project, title) {
        if (testGitExecutable) {
          val executableManager = serviceAsync<GitExecutableManager>()
          val valid = withContext(Dispatchers.IO) {
            executableManager.testGitExecutableVersionValid(project)
          }
          if (!valid) {
            currentCoroutineContext().cancel(CancellationException("Invalid git executable"))
            awaitCancellation()
          }
        }
        checkCanceled()

        for (root in roots) {
          repositoryManager.addExternalRepository(root, GitRepositoryImpl.createInstance(root, project, repositoriesDisposable))
        }
        val properties = serviceAsync<GitExternalLogTabsProperties>()
        val vcs = GitVcs.getInstance(project)
        val logProviders = VcsLogManager.findLogProviders(roots.map { VcsRoot(vcs, it) }, project)
        val name = "Vcs Log for " + getProvidersMapText(logProviders)
        VcsLogManager(project, cs, properties, logProviders, name, false, null).apply {
          initialize()
        }
      }

      try {
        checkCanceled()
        val ui = withContext(Dispatchers.UiWithModelAccess) {
          manager.showLog()
        }
        Disposer.register(ui, cs::cancel)
        try {
          awaitCancellation()
        }
        finally {
          withContext(NonCancellable + Dispatchers.UI) {
            Disposer.dispose(ui)
          }
        }
      }
      finally {
        withContext(NonCancellable) {
          Disposer.dispose(repositoriesDisposable)
          roots.forEach { repositoryManager.removeExternalRepository(it) }
          manager.dispose()
        }
      }
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

private fun selectProjectLog(project: Project, roots: List<VirtualFile>): Boolean {
  val vcs = GitVcs.getInstance(project)
  val projectRoots = listOf(*ProjectLevelVcsManager.getInstance(project).getRootsUnderVcs(vcs))
  if (!projectRoots.containsAll(roots)) return false

  if (roots.containsAll(projectRoots)) return VcsLogContentUtil.selectMainLog(project)
  val filters = collection(fromRoots(roots))
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
