// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.providers

import com.intellij.ide.actions.searcheverywhere.AbstractGotoSEContributor
import com.intellij.ide.actions.searcheverywhere.FilesTabSEContributor
import com.intellij.ide.actions.searcheverywhere.FilesTabSEContributor.Companion.unwrapFilesTabContributorIfPossible
import com.intellij.ide.actions.searcheverywhere.ScopeChooserAction
import com.intellij.ide.util.scopeChooser.ScopeDescriptor
import com.intellij.ide.util.scopeChooser.ScopeIdMapper
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.platform.scopes.SearchScopeData
import com.intellij.platform.scopes.SearchScopesInfo
import com.intellij.platform.searchEverywhere.utils.SuspendLazyProperty
import com.intellij.platform.searchEverywhere.utils.suspendLazy
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.annotations.ApiStatus
import java.util.UUID
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@OptIn(ExperimentalAtomicApi::class)
@ApiStatus.Internal
class ScopeChooserActionProviderDelegate private constructor(private val contributorWrapper: SeAsyncContributorWrapper<Any>) {

  val searchScopesInfo: SuspendLazyProperty<SearchScopesInfo?> = suspendLazy { getSearchScopesInfo() }

  private val availableScopes: AtomicReference<SeScopeById> =
    contributorWrapper.contributor.unwrapFilesTabContributorIfPossible()?.let { filesTabContributor ->
      AtomicReference(SeScopeByIdFiles(filesTabContributor))
    } ?: AtomicReference(SeScopeByIdAtomicMap(emptyMap(), null, null))

  private suspend fun getSearchScopesInfo(): SearchScopesInfo? {
    val scopeChooserAction: ScopeChooserAction = contributorWrapper.contributor.getActions { }.filterIsInstance<ScopeChooserAction>().firstOrNull()
                                                 ?: return null


    val all = mutableMapOf<String, ScopeDescriptor>()
    val selectedScope = scopeChooserAction.selectedScope

    val scopeDataList = readAction {
      scopeChooserAction.scopesWithSeparators
    }.mapNotNull { scope ->
      val key = scope.displayName?.let {
        "${UUID.randomUUID()}$SCOPE_ID_SEPARATOR${ScopeIdMapper.instance.getScopeSerializationId(it)}"
      } ?: return@mapNotNull null

      val data = SearchScopeData.from(scope, key)
      if (data != null) all[key] = scope
      data
    }

    val selectedScopeId = selectedScope.scope?.displayName.let { name ->
      scopeDataList.firstOrNull {
        it.name == name
      }?.scopeId
    }

    val everywhereScopeId = scopeChooserAction.everywhereScopeName?.let { name ->
      scopeDataList.firstOrNull {
        it.name == name
      }?.scopeId
    }

    val projectScopeId = scopeChooserAction.projectScopeName?.let { name ->
      scopeDataList.firstOrNull {
        it.name == name
      }?.scopeId
    }

    availableScopes.store(SeScopeByIdAtomicMap(all, everywhereScopeId = everywhereScopeId, projectScopeId = projectScopeId))

    return SearchScopesInfo(scopeDataList,
                            selectedScopeId,
                            projectScopeId,
                            everywhereScopeId)
  }

  fun applyScope(isEverywhere: Boolean, isAutoTogglePossible: Boolean) {
    val scope = availableScopes.load()[isEverywhere] ?: return
    applyScope(scope, isAutoTogglePossible)
  }

  fun applyScope(scopeId: String?, isAutoTogglePossible: Boolean) {
    if (scopeId == null) return
    val scope = availableScopes.load()[scopeId] ?: return
    applyScope(scope, isAutoTogglePossible)
  }

  fun applyScope(scope: ScopeDescriptor, isAutoTogglePossible: Boolean) {
    contributorWrapper.contributor.getActions { }.filterIsInstance<ScopeChooserAction>().firstOrNull()?.let {
      it.setScopeIsDefaultAndAutoSet(isAutoTogglePossible)
      it.onScopeSelected(scope)
    }
    ?: contributorWrapper.contributor.unwrapFilesTabContributorIfPossible()?.setScope(scope)
  }

  companion object {
    fun createOrNull(contributorWrapper: SeAsyncContributorWrapper<Any>): ScopeChooserActionProviderDelegate? =
      if (contributorWrapper.contributor.unwrapFilesTabContributorIfPossible() != null ||
          contributorWrapper.contributor.getActions { }.any { it is ScopeChooserAction })
        ScopeChooserActionProviderDelegate(contributorWrapper)
      else null
  }
}

private const val SCOPE_ID_SEPARATOR = '_'

private interface SeScopeById {
  operator fun get(isEverywhere: Boolean): ScopeDescriptor?
  operator fun get(scopeId: String): ScopeDescriptor?
}

private class SeScopeByIdAtomicMap(
  private val scopeIdToScope: Map<String, ScopeDescriptor>,
  private val everywhereScopeId: String?,
  private val projectScopeId: String?,
) : SeScopeById {
  override fun get(isEverywhere: Boolean): ScopeDescriptor? =
    (if (isEverywhere) everywhereScopeId else projectScopeId)?.let { scopeIdToScope[it] }

  override fun get(scopeId: String): ScopeDescriptor? = scopeIdToScope[scopeId]
}

private class SeScopeByIdFiles(filesContributor: FilesTabSEContributor): SeScopeById {
  private val scopes = runReadActionBlocking {
    AbstractGotoSEContributor.createScopes(filesContributor.project, filesContributor.psiContext).mapNotNull {
      val name = it.displayName ?: return@mapNotNull null
      ScopeIdMapper.instance.getScopeSerializationId(name) to it
    }.toMap()
  }

  private val projectScopeId: String = GlobalSearchScope.projectScope(filesContributor.project).displayName.let {
    ScopeIdMapper.instance.getScopeSerializationId(it)
  }

  private val everywhereScopeId: String = GlobalSearchScope.everythingScope(filesContributor.project).displayName.let {
    ScopeIdMapper.instance.getScopeSerializationId(it)
  }

  override fun get(isEverywhere: Boolean): ScopeDescriptor? = (if (isEverywhere) everywhereScopeId else projectScopeId).let { scopes[it] }
  override fun get(scopeId: String): ScopeDescriptor? = scopes[scopeId.substringAfter(SCOPE_ID_SEPARATOR)]
}
