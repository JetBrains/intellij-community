// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest

import com.intellij.testFramework.LightVirtualFile
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestShort
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataContext

internal class GHPRVirtualFile(val dataContext: GHPRDataContext, val pullRequest: GHPullRequestShort)
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