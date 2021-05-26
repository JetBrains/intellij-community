// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest

import com.intellij.diff.chains.DiffRequestChain
import com.intellij.openapi.Disposable

interface GHPRDiffRequestModel {

  var requestChain: DiffRequestChain?

  fun addAndInvokeRequestChainListener(disposable: Disposable, listener: () -> Unit)
}
