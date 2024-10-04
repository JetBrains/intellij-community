// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.configurable

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.NaturalComparator
import com.intellij.openapi.vcs.AbstractVcs
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls

@ApiStatus.Internal
object SuggestedVcsComparator {
  private const val GIT_VCS_NAME: @NonNls String = "Git"

  @JvmStatic
  fun create(project: Project): Comparator<AbstractVcs?> {
    val activeVcss = ProjectLevelVcsManager.getInstance(project).getAllActiveVcss();
    return compareBy<AbstractVcs?> { vcs ->
      when {
        vcs == null -> 1 // <None> is last
        activeVcss.contains(vcs) -> -2
        vcs.name == GIT_VCS_NAME -> -1
        else -> 0
      }
    }.thenComparing({ vcs -> vcs?.displayName ?: "" }, NaturalComparator.INSTANCE)
  }
}