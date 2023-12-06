// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest

import com.intellij.diff.chains.DiffRequestChain
import com.intellij.openapi.Disposable
import com.intellij.openapi.vcs.FilePath

interface GHPRDiffRequestModel {

  var requestChain: DiffRequestChain?
  var selectedFilePath: FilePath?

  fun addAndInvokeRequestChainListener(disposable: Disposable, listener: () -> Unit)
  fun addFilePathSelectionListener(listener: () -> Unit)
  fun addFilePathSelectionListener(disposable: Disposable, listener: () -> Unit)
}
