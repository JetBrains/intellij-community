// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest

import com.intellij.diff.chains.DiffRequestChain
import com.intellij.openapi.Disposable
import com.intellij.openapi.ListSelection
import com.intellij.openapi.vcs.changes.Change
import com.intellij.util.EventDispatcher
import org.jetbrains.plugins.github.pullrequest.ui.SimpleEventListener
import org.jetbrains.plugins.github.util.GithubUtil.Delegates.observableField

class GHPRDiffRequestModelImpl : GHPRDiffRequestModel {

  private val eventDispatcher = EventDispatcher.create(SimpleEventListener::class.java)

  override var requestChain: DiffRequestChain? by observableField<DiffRequestChain?>(null, eventDispatcher)

  override fun addAndInvokeRequestChainListener(disposable: Disposable, listener: () -> Unit) =
    SimpleEventListener.addAndInvokeListener(eventDispatcher, disposable, listener)
}