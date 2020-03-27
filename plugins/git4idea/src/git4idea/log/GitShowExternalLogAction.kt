// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.log

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.components.ServiceManager
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
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsRoot
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindowManager.Companion.getInstance
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.content.ContentManager
import com.intellij.vcs.log.VcsLogBundle
import com.intellij.vcs.log.impl.VcsLogContentUtil
import com.intellij.vcs.log.impl.VcsLogManager
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
import java.awt.BorderLayout
import java.io.File
import java.util.*
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
      if (!checkIfProjectLogMatches(project, vcs, cm, roots) && !checkIfAlreadyOpened(cm, roots)) {
        val tabName = calcTabName(cm, roots)
        val component = createManagerAndContent(project, vcs, roots, true)
        val content = ContentFactory.SERVICE.getInstance().createContent(component, tabName, false)
        content.setDisposer(component.disposable)
        content.description = GitBundle.message("git.log.external.tab.description",
                                                StringUtil.join(roots, { obj: VirtualFile -> obj.path }, "\n"))
        content.isCloseable = true
        cm.addContent(content)
        cm.setSelectedContent(content)
      }
    }
    if (!window.isVisible) {
      window.activate(showContent, true)
    }
    else {
      showContent()
    }
  }

  private class MyContentComponent internal constructor(actualComponent: JComponent,
                                                        val roots: Collection<VirtualFile>,
                                                        val disposable: Disposable) : JPanel(BorderLayout()) {

    init {
      add(actualComponent)
    }
  }

  private class ShowLogInDialogTask(project: Project, val roots: List<VirtualFile>, val vcs: GitVcs) :
    Backgroundable(project, GitBundle.message(
      "git.log.external.loading.process"), true) {
    override fun run(indicator: ProgressIndicator) {
      if (!GitExecutableManager.getInstance().testGitExecutableVersionValid(myProject)) {
        throw ProcessCanceledException()
      }
    }

    override fun onSuccess() {
      if (!myProject.isDisposed) {
        val content = createManagerAndContent(myProject, vcs, roots, false)
        val window = WindowWrapperBuilder(WindowWrapper.Mode.FRAME, content)
          .setProject(myProject)
          .setTitle(GitBundle.message("git.log.external.window.title"))
          .setPreferredFocusedComponent(content)
          .setDimensionServiceKey(GitShowExternalLogAction::class.java.name)
          .build()
        Disposer.register(window, content.disposable)
        window.show()
      }
    }
  }

  companion object {
    private const val EXTERNAL = "EXTERNAL"

    private fun createManagerAndContent(project: Project,
                                        vcs: GitVcs,
                                        roots: List<VirtualFile>,
                                        isToolWindowTab: Boolean): MyContentComponent {
      val disposable = Disposer.newDisposable()
      val repositoryManager = GitRepositoryManager.getInstance(project)
      for (root in roots) {
        repositoryManager.addExternalRepository(root, GitRepositoryImpl.createInstance(root, project, disposable, true))
      }
      val manager = VcsLogManager(project, ServiceManager.getService(GitExternalLogTabsProperties::class.java),
                                  roots.map { VcsRoot(vcs, it) })
      Disposer.register(disposable, Disposable { manager.dispose { roots.forEach { repositoryManager.removeExternalRepository(it) } } })
      val ui = manager.createLogUi(calcLogId(roots),
                                   if (isToolWindowTab) VcsLogManager.LogWindowKind.TOOL_WINDOW else VcsLogManager.LogWindowKind.STANDALONE,
                                   true)
      Disposer.register(disposable, ui)
      return MyContentComponent(VcsLogPanel(manager, ui), roots, disposable)
    }

    private fun calcLogId(roots: List<VirtualFile>): String {
      return "$EXTERNAL " + StringUtil.join(roots, { obj: VirtualFile -> obj.path }, File.pathSeparator)
    }

    private fun calcTabName(cm: ContentManager, roots: List<VirtualFile>): String {
      var name = VcsLogBundle.message("vcs.log.tab.name") + " (" + roots[0].name
      if (roots.size > 1) {
        name += "+"
      }
      name += ")"
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
      if (virtualFiles.isEmpty()) {
        return emptyList()
      }
      val correctRoots: MutableList<VirtualFile> = ArrayList()
      for (vf in virtualFiles) {
        if (GitUtil.isGitRoot(File(vf.path))) {
          correctRoots.add(vf)
        }
      }
      return correctRoots
    }

    private fun checkIfProjectLogMatches(project: Project,
                                         vcs: GitVcs,
                                         cm: ContentManager,
                                         requestedRoots: List<VirtualFile>): Boolean {
      val projectRoots = listOf(*ProjectLevelVcsManager.getInstance(project).getRootsUnderVcs(vcs))
      return if (projectRoots.containsAll(requestedRoots)) {
        if (requestedRoots.containsAll(projectRoots)) {
          VcsLogContentUtil.selectMainLog(cm)
        }
        else {
          val filters = collection(fromRoots(requestedRoots))
          VcsProjectLog.getInstance(project).openLogTab(filters) != null
        }
      }
      else false
    }

    private fun checkIfAlreadyOpened(cm: ContentManager, roots: Collection<VirtualFile>): Boolean {
      for (content in cm.contents) {
        val component = content.component
        if (component is MyContentComponent) {
          if (Comparing.haveEqualElements(roots, component.roots)) {
            cm.setSelectedContent(content)
            return true
          }
        }
      }
      return false
    }
  }
}