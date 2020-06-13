// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.changes

import com.intellij.diff.chains.AsyncDiffRequestChain
import com.intellij.diff.chains.DiffRequestChain
import com.intellij.diff.chains.DiffRequestProducer
import com.intellij.diff.impl.CacheDiffRequestProcessor
import com.intellij.diff.util.DiffUserDataKeysEx.ScrollToPolicy
import com.intellij.openapi.project.Project
import kotlin.properties.Delegates

//TODO: changes navigation popup
class MutableDiffRequestChainProcessor(project: Project, chain: DiffRequestChain?)
  : CacheDiffRequestProcessor.Simple(project) {

  private val asyncChangeListener = AsyncDiffRequestChain.Listener {
    dropCaches()
    currentIndex = (this.chain?.index ?: 0)
    updateRequest(true)
  }

  var chain: DiffRequestChain? by Delegates.observable<DiffRequestChain?>(null) { _, oldValue, newValue ->
    if (oldValue is AsyncDiffRequestChain) {
      oldValue.onAssigned(false)
      oldValue.removeListener(asyncChangeListener)
    }

    if (newValue is AsyncDiffRequestChain) {
      newValue.onAssigned(true)
      // listener should be added after `onAssigned` call to avoid notification about synchronously loaded requests
      newValue.addListener(asyncChangeListener, this)
    }
    currentIndex = newValue?.index ?: 0
    updateRequest()
  }
  private var currentIndex: Int = 0

  init {
    this.chain = chain
  }

  override fun onDispose() {
    val chain = chain
    if (chain is AsyncDiffRequestChain) chain.onAssigned(false)

    super.onDispose()
  }

  override fun getCurrentRequestProvider(): DiffRequestProducer? {
    val requests = chain?.requests ?: return null
    return if (currentIndex < 0 || currentIndex >= requests.size) null else requests[currentIndex]
  }

  override fun hasNextChange(fromUpdate: Boolean): Boolean {
    val chain = chain ?: return false
    return currentIndex < chain.requests.lastIndex
  }

  override fun hasPrevChange(fromUpdate: Boolean): Boolean {
    val chain = chain ?: return false
    return currentIndex > 0 && chain.requests.size > 1
  }

  override fun goToNextChange(fromDifferences: Boolean) {
    currentIndex += 1
    updateRequest(false, if (fromDifferences) ScrollToPolicy.FIRST_CHANGE else null)
  }

  override fun goToPrevChange(fromDifferences: Boolean) {
    currentIndex -= 1
    updateRequest(false, if (fromDifferences) ScrollToPolicy.LAST_CHANGE else null)
  }

  override fun isNavigationEnabled(): Boolean {
    val chain = chain ?: return false
    return chain.requests.size > 1
  }
}