// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.backend.providers

import com.intellij.ide.actions.searcheverywhere.ScopeChooserAction
import com.intellij.ide.util.scopeChooser.ScopeDescriptor
import com.intellij.openapi.application.readAction
import com.intellij.platform.scopes.SearchScopeData
import com.intellij.platform.scopes.SearchScopesInfo
import com.intellij.platform.searchEverywhere.providers.SeAsyncWeightedContributorWrapper
import com.intellij.platform.searchEverywhere.utils.SuspendLazyProperty
import com.intellij.platform.searchEverywhere.utils.suspendLazy
import org.jetbrains.annotations.ApiStatus
import java.util.*
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@OptIn(ExperimentalAtomicApi::class)
@ApiStatus.Internal
class ScopeChooserActionProviderDelegate(private val contributorWrapper: SeAsyncWeightedContributorWrapper<Any>) {

  val searchScopesInfo: SuspendLazyProperty<SearchScopesInfo?> = suspendLazy { getSearchScopesInfo() }

  @Volatile
  private var scopeIdToScope: AtomicReference<Map<String, ScopeDescriptor>> = AtomicReference(emptyMap())

  suspend fun getSearchScopesInfo(): SearchScopesInfo? {
    val scopeChooserAction: ScopeChooserAction = contributorWrapper.contributor.getActions({ }).filterIsInstance<ScopeChooserAction>().firstOrNull()
                                                 ?: return null


    val all = mutableMapOf<String, ScopeDescriptor>()
    val selectedScope = scopeChooserAction.selectedScope

    val scopeDataList = readAction {
      scopeChooserAction.scopesWithSeparators
    }.mapNotNull { scope ->
      val key = UUID.randomUUID().toString()
      val data = SearchScopeData.from(scope, key)
      if (data != null) all[key] = scope
      data
    }
    scopeIdToScope.store(all)

    val selectedScopeId = selectedScope.scope?.displayName.let { name ->
      scopeDataList.firstOrNull {
        @Suppress("HardCodedStringLiteral")
        it.name == name
      }?.scopeId
    }

    val everywhereScopeId = scopeChooserAction.everywhereScopeName?.let { name ->
      scopeDataList.firstOrNull {
        @Suppress("HardCodedStringLiteral")
        it.name == name
      }?.scopeId
    }

    val projectScopeId = scopeChooserAction.projectScopeName?.let { name ->
      scopeDataList.firstOrNull {
        @Suppress("HardCodedStringLiteral")
        it.name == name
      }?.scopeId
    }

    return SearchScopesInfo(scopeDataList,
                            selectedScopeId,
                            projectScopeId,
                            everywhereScopeId)
  }

  fun applyScope(scopeId: String?) {
    if (scopeId == null) return
    val scope = scopeIdToScope.load()[scopeId] ?: return

    contributorWrapper.contributor.getActions { }.filterIsInstance<ScopeChooserAction>().firstOrNull()?.onScopeSelected(scope)
  }
}