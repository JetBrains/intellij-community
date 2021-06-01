// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.ift.lesson

import com.intellij.openapi.project.Project
import git4idea.ift.GitProjectUtil
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import training.learn.course.KLesson

abstract class GitLesson(@NonNls id: String, @Nls name: String) : KLesson(id, name) {
  override fun prepare(project: Project) {
    GitProjectUtil.restoreGitLessonsFiles(project)
  }
}