// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.log

import com.intellij.openapi.Disposable
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
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsRoot
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindowManager.Companion.getInstance
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.content.ContentManager
import com.intellij.vcs.log.VcsLogBundle
import com.intellij.vcs.log.VcsLogProvider
import com.intellij.vcs.log.impl.VcsLogContentUtil
import com.intellij.vcs.log.impl.VcsLogManager
import com.intellij.vcs.log.impl.VcsLogTabLocation
import com.intellij.vcs.log.impl.VcsProjectLog
import com.intellij.vcs.log.ui.VcsLogPanel
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
      ProgressManager.getInstance().run(ShowLogInDialogTask(project, roots, vcs))
      return
    }
    val showContent = {
      val cm = window.contentManager
      if (!selectProjectLog(project, vcs, roots) && !selectAlreadyOpened(cm, roots)) {
        val component = createManagerAndContent(project, vcs, roots, true)
        val content = ContentFactory.SERVICE.getInstance().createContent(component, calcTabName(cm, roots), false)
        content.setDisposer(component.disposable)
        content.description = GitBundle.message("git.log.external.tab.description",
                                                roots.joinToString("\n") { obj: VirtualFile -> obj.path })
        content.isCloseable = true
        cm.addContent(content)
        cm.setSelectedContent(content)
        doOnProviderRemoval(project, component.disposable) { cm.removeContent(content, true) }
      }
    }
    if (!window.isVisible) {
      window.activate(showContent, true)
    }
    else {
      showContent()
    }
  }
}

private class MyContentComponent(actualComponent: JComponent,
                                 val roots: Collection<VirtualFile>,
                                 val disposable: Disposable) : JPanel(BorderLayout()) {

  init {
    add(actualComponent)
  }
}

private class ShowLogInDialogTask(project: Project, val roots: List<VirtualFile>, val vcs: GitVcs) :
  Backgroundable(project, @Suppress("DialogTitleCapitalization") GitBundle.message("git.log.external.loading.process"), true) {
  override fun run(indicator: ProgressIndicator) {
    if (!GitExecutableManager.getInstance().testGitExecutableVersionValid(project)) {
      throw ProcessCanceledException()
    }
  }

  override fun onSuccess() {
    if (!project.isDisposed) {
      val content = createManagerAndContent(project, vcs, roots, false)
      val window = WindowWrapperBuilder(WindowWrapper.Mode.FRAME, content)
        .setProject(project)
        .setTitle(GitBundle.message("git.log.external.window.title"))
        .setPreferredFocusedComponent(content)
        .setDimensionServiceKey(GitShowExternalLogAction::class.java.name)
        .build()
      Disposer.register(window, content.disposable)
      doOnProviderRemoval(project, content.disposable) { window.close() }
      window.show()
    }
  }
}

private const val EXTERNAL = "EXTERNAL"

private fun createManagerAndContent(project: Project,
                                    vcs: GitVcs,
                                    roots: List<VirtualFile>,
                                    isToolWindowTab: Boolean): MyContentComponent {
  val disposable = Disposer.newDisposable()
  val repositoryManager = GitRepositoryManager.getInstance(project)
  for (root in roots) {
    repositoryManager.addExternalRepository(root, GitRepositoryImpl.createInstance(root, project, disposable))
  }
  val manager = VcsLogManager(project, ApplicationManager.getApplication().getService(GitExternalLogTabsProperties::class.java),
                              roots.map { VcsRoot(vcs, it) })
  Disposer.register(disposable, Disposable { manager.dispose { roots.forEach { repositoryManager.removeExternalRepository(it) } } })
  val ui = manager.createLogUi(calcLogId(roots),
                               if (isToolWindowTab) VcsLogTabLocation.TOOL_WINDOW else VcsLogTabLocation.STANDALONE)
  Disposer.register(disposable, ui)
  return MyContentComponent(VcsLogPanel(manager, ui), roots, disposable)
}

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
  return virtualFiles.filter { GitUtil.isGitRoot(File(it.path)) }
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