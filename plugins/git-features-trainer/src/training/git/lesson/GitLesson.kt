// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package training.git.lesson

import com.intellij.openapi.project.Project
import git4idea.config.GitExecutableManager
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import training.dsl.LessonContext
import training.git.GitLessonsUtil.refreshGitLogOnOpen
import training.git.GitProjectUtil
import training.learn.course.KLesson
import training.learn.course.LessonProperties
import training.learn.exceptons.LessonPreparationException

abstract class GitLesson(@NonNls id: String, @Nls name: String) : KLesson(id, name) {
  protected abstract val branchName: String
  override val properties = LessonProperties(availableSince = "212")

  override val fullLessonContent: LessonContext.() -> Unit
    get() = {
      refreshGitLogOnOpen()
      super.fullLessonContent(this)
    }

  override fun prepare(project: Project) {
    if (GitExecutableManager.getInstance().testGitExecutableVersionValid(project)) {
      GitProjectUtil.restoreGitLessonsFiles(project, branchName)
    }
    else throw LessonPreparationException("Git is not installed or version is invalid")
  }
}
