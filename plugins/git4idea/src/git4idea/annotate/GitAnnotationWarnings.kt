// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.annotate

import com.intellij.ide.IdeBundle
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.annotate.AnnotationWarning
import com.intellij.ui.AppUIUtil.invokeLaterIfProjectAlive
import git4idea.actions.GitUnshallowRepositoryAction
import git4idea.i18n.GitBundle
import git4idea.repo.GitRepositoryManager

@Service(Service.Level.PROJECT)
internal class GitAnnotationWarnings(private val project: Project) {
  fun getAnnotationWarnings(gitFileAnnotation: GitFileAnnotation): AnnotationWarning? {
    if (PropertiesComponent.getInstance(project).getBoolean(WARNINGS_DISMISSED_KEY, false)) return null

    val anyRepoIsShallow = GitRepositoryManager.getInstance(project).repositories.any { repository -> repository.info.isShallow }
    if (!anyRepoIsShallow) return null

    val repository = GitRepositoryManager.getInstance(project)
      .getRepositoryForFileQuick(gitFileAnnotation.file)?.takeIf { it.info.isShallow }?: return null

    val unshallowAction = object : AnnotationWarning.Action(GitBundle.message("action.Git.Unshallow.text")) {
      override fun doAction(hideWarning: Runnable) {
        GitUnshallowRepositoryAction.unshallowRepository(repository) {
          invokeLaterIfProjectAlive(project) {
            hideWarning.run()
          }
        }
      }
    }

    val dontShowAgainAction = object : AnnotationWarning.Action(IdeBundle.message("label.dont.show")) {
      override fun doAction(hideWarning: Runnable) {
        PropertiesComponent.getInstance(project).setValue(WARNINGS_DISMISSED_KEY, true)
        hideWarning.run()
      }
    }

    return AnnotationWarning.warning(GitBundle.message("annotate.repository.is.shallow"), listOf(unshallowAction, dontShowAgainAction))
  }

  companion object {
    private const val WARNINGS_DISMISSED_KEY = "git.annotate.shallow.warnings.dismissed"

    @JvmStatic
    fun getInstance(project: Project): GitAnnotationWarnings = project.getService(GitAnnotationWarnings::class.java)
  }
}
