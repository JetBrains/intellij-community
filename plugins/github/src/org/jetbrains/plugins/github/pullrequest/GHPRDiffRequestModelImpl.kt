// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest

import com.intellij.diff.chains.DiffRequestChain
import com.intellij.openapi.Disposable
import com.intellij.openapi.vcs.FilePath
import com.intellij.util.EventDispatcher
import com.intellij.collaboration.ui.SimpleEventListener
import org.jetbrains.plugins.github.util.GithubUtil.Delegates.observableField

class GHPRDiffRequestModelImpl : GHPRDiffRequestModel {

  private val eventDispatcher = EventDispatcher.create(SimpleEventListener::class.java)
  private val selectedPathDispatcher = EventDispatcher.create(SimpleEventListener::class.java)

  override var requestChain: DiffRequestChain? by observableField(null, eventDispatcher)
  override var selectedFilePath: FilePath? by observableField(null, selectedPathDispatcher)

  override fun addAndInvokeRequestChainListener(disposable: Disposable, listener: () -> Unit) =
    SimpleEventListener.addAndInvokeListener(eventDispatcher, disposable, listener)

  override fun addFilePathSelectionListener(listener: () -> Unit) {
    SimpleEventListener.addListener(selectedPathDispatcher, listener)
  }
}
