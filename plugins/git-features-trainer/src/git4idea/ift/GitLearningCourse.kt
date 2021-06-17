// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.ift

import git4idea.ift.lesson.*
import training.learn.course.IftModule
import training.learn.course.KLesson
import training.learn.course.LearningCourse
import training.learn.course.LessonType

class GitLearningCourse : LearningCourse {
  override fun modules(): Collection<IftModule> {
    val initLessons: () -> List<KLesson> = {
      listOf(GitQuickStartLesson(),
             GitProjectHistoryLesson(),
             GitCommitLesson(),
             GitFeatureBranchWorkflowLesson(),
             GitInteractiveRebaseLesson(),
             GitChangelistsAndShelveLesson(),
             GitAnnotateLesson())
    }
    val module = object : IftModule(GitLessonsBundle.message("git.module.name"), GitLessonsBundle.message("git.module.description"),
                                    null, LessonType.PROJECT, initLessons) {
      override val sanitizedName: String = ""
    }
    return listOf(module)
  }
}