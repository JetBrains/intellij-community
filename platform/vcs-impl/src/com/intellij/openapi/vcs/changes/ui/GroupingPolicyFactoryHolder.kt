// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProcessCanceledException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class)
@Service
class GroupingPolicyFactoryHolder(private val cs: CoroutineScope) {
  var factories: AvailableFactories = buildFactories()

  init {
    ChangesGroupingPolicyFactory.EP_NAME.addChangeListener(::buildFactoriesAndSave, null)

  }

  private fun buildFactoriesAndSave() {
    cs.launch {
      factories = buildFactories()
    }
  }


  private fun buildFactories(): AvailableFactories {
    val keyToFactory = mutableMapOf<String, ChangesGroupingPolicyFactory>()
    val keyToWeight = mutableMapOf<String, Int>()

    for (bean in ChangesGroupingPolicyFactory.EP_NAME.extensionList) {
      val key = bean.key ?: continue
      try {
        keyToFactory[key] = bean.instance
        keyToWeight[key] = bean.weight
      }
      catch (e: ProcessCanceledException) {
        throw e
      }
      catch (e: Throwable) {
        logger<ChangesGroupingSupport>().error(e)
      }
    }
    return AvailableFactories(keyToFactory, keyToWeight)
  }

  companion object {
    fun getInstance() = service<GroupingPolicyFactoryHolder>()
  }
}

class AvailableFactories(
  val keyToFactory: Map<String, ChangesGroupingPolicyFactory>,
  val keyToWeight: Map<String, Int>,
)