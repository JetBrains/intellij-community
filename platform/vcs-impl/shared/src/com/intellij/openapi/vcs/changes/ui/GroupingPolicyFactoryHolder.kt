// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.platform.vcs.impl.shared.rhizome.GroupingItemEntity
import fleet.kernel.change
import fleet.kernel.shared
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus

@OptIn(FlowPreview::class)
@ApiStatus.Internal
@Service
class GroupingPolicyFactoryHolder(private val cs: CoroutineScope) {
  var factories: AvailableFactories = buildFactories()
  private val saveToDbFlow = MutableSharedFlow<Unit>()

  init {
    ChangesGroupingPolicyFactory.EP_NAME.addChangeListener(::buildFactoriesAndSave, null)
    cs.launch {
      saveToDbFlow.debounce(DEBOUNCE_TIMEOUT_MS).collectLatest {
        saveGroupingKeysToDb()
      }
    }
  }

  private fun buildFactoriesAndSave() {
    cs.launch {
      factories = buildFactories()
      saveToDbFlow.emit(Unit)
    }
  }

  suspend fun saveGroupingKeysToDb() {
    change {
      shared {
        GroupingItemEntity.all().forEach { it.delete() }
        for (groupingKey in factories.keyToFactory.keys) {
          GroupingItemEntity.new {
            it[GroupingItemEntity.Name] = groupingKey
          }
        }
      }
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

@ApiStatus.Internal
class AvailableFactories(
  val keyToFactory: Map<String, ChangesGroupingPolicyFactory>,
  val keyToWeight: Map<String, Int>,
)

private const val DEBOUNCE_TIMEOUT_MS = 200L