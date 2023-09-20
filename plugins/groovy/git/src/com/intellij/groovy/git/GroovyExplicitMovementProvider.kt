// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.groovy.git

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Couple
import com.intellij.openapi.vcs.FilePath
import git4idea.checkin.GitCheckinExplicitMovementProvider
import org.jetbrains.plugins.groovy.refactoring.convertToJava.git.pathBeforeGroovyToJavaConversion

class GroovyExplicitMovementProvider : GitCheckinExplicitMovementProvider() {
  override fun isEnabled(project: Project): Boolean {
    return true
  }

  override fun getDescription(): String {
    return GroovyGitBundle.message("groovyToJava.extra.commit.description")
  }

  override fun getCommitMessage(oldCommitMessage: String): String {
    return GroovyGitBundle.message("groovyToJava.extra.commit.commit.message")
  }

  override fun collectExplicitMovements(
    project: Project,
    beforePaths: List<FilePath>,
    afterPaths: List<FilePath>
  ): Collection<Movement> {
    val movedChanges = ArrayList<Movement>()
    for (after in afterPaths) {
      val pathBeforeGroovyToJava = after.virtualFile?.pathBeforeGroovyToJavaConversion
      if (pathBeforeGroovyToJava != null) {
        val before = beforePaths.firstOrNull { it.path == pathBeforeGroovyToJava }
        if (before != null) {
          movedChanges.add(Movement(before, after))
        }
      }
    }

    return movedChanges
  }

  override fun afterMovementsCommitted(project: Project, movedPaths: MutableList<Couple<FilePath>>) {
    movedPaths.forEach { it.second.virtualFile?.pathBeforeGroovyToJavaConversion = null }
  }
}
