// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest

import com.intellij.testFramework.LightVirtualFile
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestShort
import org.jetbrains.plugins.github.pullrequest.action.GHPRActionDataContext

internal class GHPRVirtualFile(val context: GHPRActionDataContext, private val pullRequest: GHPullRequestShort)
  : LightVirtualFile(pullRequest.title, GHPRFileType.INSTANCE, "") {

  init {
    isWritable = false
  }

  override fun getPath(): String = pullRequest.url

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is GHPRVirtualFile) return false

    if (pullRequest != other.pullRequest) return false

    return true
  }

  override fun hashCode(): Int {
    return pullRequest.hashCode()
  }
}