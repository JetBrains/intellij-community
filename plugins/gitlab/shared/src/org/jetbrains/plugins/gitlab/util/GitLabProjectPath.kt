// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.util

import com.intellij.openapi.util.NlsSafe
import com.intellij.util.text.nullize
import kotlinx.serialization.Serializable

@Serializable
data class GitLabProjectPath(val owner: @NlsSafe String, val name: @NlsSafe String) {
  @NlsSafe
  fun fullPath(withOwner: Boolean = true): String = if (withOwner) "$owner/$name" else name

  @NlsSafe
  override fun toString(): String = "$owner/$name"

  companion object {
    fun extractProjectPath(repositoryPath: String): GitLabProjectPath? {
      val lastSlashIdx = repositoryPath.lastIndexOf('/')
      if (lastSlashIdx < 0) return null

      val name = repositoryPath.substringAfterLast('/', "").nullize() ?: return null
      val owner = repositoryPath.substringBeforeLast('/', "").nullize() ?: return null
      return GitLabProjectPath(owner, name)
    }
  }
}
