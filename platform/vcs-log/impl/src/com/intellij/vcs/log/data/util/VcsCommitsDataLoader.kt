// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.data.util

import com.intellij.openapi.Disposable
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.vcs.log.CommitId

interface VcsCommitsDataLoader<T> : Disposable {

  @RequiresEdt
  fun loadData(commits: List<CommitId>, onChange: (Map<CommitId, T>) -> Unit)

}