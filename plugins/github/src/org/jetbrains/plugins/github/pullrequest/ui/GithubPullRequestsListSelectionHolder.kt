// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui

import com.intellij.openapi.Disposable
import org.jetbrains.annotations.CalledInAwt

internal interface GithubPullRequestsListSelectionHolder {
  @get:CalledInAwt
  @set:CalledInAwt
  var selectionNumber: Long?

  @CalledInAwt
  fun addSelectionChangeListener(disposable: Disposable, listener: () -> Unit)
}