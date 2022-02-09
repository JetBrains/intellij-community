// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.ift

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowType
import com.intellij.openapi.wm.WindowInfo
import com.intellij.openapi.wm.ex.ToolWindowManagerEx
import git4idea.ift.lesson.*
import org.jetbrains.annotations.NonNls
import training.learn.course.IftModule
import training.learn.course.KLesson
import training.learn.course.LearningCourse
import training.learn.course.LessonType

class GitLearningCourse : LearningCourse {
  override fun modules(): Collection<IftModule> {
    return listOf(GitLearningModule("Git") {
      listOf(GitQuickStartLesson(),
             GitProjectHistoryLesson(),
             GitCommitLesson(),
             GitFeatureBranchWorkflowLesson(),
             GitInteractiveRebaseLesson(),
             GitChangelistsAndShelveLesson(),
             GitAnnotateLesson())
    })
  }

  private class GitLearningModule(@NonNls id: String, initLessons: () -> List<KLesson>)
    : IftModule(id, GitLessonsBundle.message("git.module.name"), GitLessonsBundle.message("git.module.description"),
                null, LessonType.PROJECT, initLessons) {
    override val sanitizedName: String = ""

    override fun preferredLearnWindowAnchor(project: Project): ToolWindowAnchor {
      val toolWindowLayout = ToolWindowManagerEx.getInstanceEx(project).getLayout()
      val commitWindowInfo = toolWindowLayout.getInfo(ToolWindowId.COMMIT)
      val vcsWindowInfo = toolWindowLayout.getInfo(ToolWindowId.VCS)
      return if (commitWindowInfo != null && vcsWindowInfo != null) {
        if (commitWindowInfo.isDockedLeft() && vcsWindowInfo.isNotOnRightOrSeparated()
            || vcsWindowInfo.isDockedLeft() && commitWindowInfo.isNotOnRightOrSeparated()) {
          ToolWindowAnchor.RIGHT
        }
        // There is only one case when we can't do something:
        // Commit and VCS window docked at left and right
        // In this case we will show Learn at default position - left
        else ToolWindowAnchor.LEFT
      }
      else {
        LOG.warn("Not found window info for tool windows: ${ToolWindowId.COMMIT}, ${ToolWindowId.VCS}")
        ToolWindowAnchor.LEFT
      }
    }

    private fun WindowInfo.isDockedLeft() = anchor == ToolWindowAnchor.LEFT && type == ToolWindowType.DOCKED

    private fun WindowInfo.isNotOnRightOrSeparated() = anchor != ToolWindowAnchor.RIGHT || type == ToolWindowType.FLOATING
                                                       || type == ToolWindowType.WINDOWED

    companion object {
      private val LOG = logger<GitLearningModule>()
    }
  }
}