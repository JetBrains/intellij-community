// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.scopes.service

import com.intellij.ide.util.scopeChooser.FrontendScopeChooser
import com.intellij.ide.util.scopeChooser.ScopeModelService
import com.intellij.ide.util.scopeChooser.ScopesFilterConditionType
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.platform.scopes.FrontendScopeChooserImpl
import kotlinx.coroutines.CoroutineScope

internal class ScopeModelServiceImpl(private val project: Project, private val coroutineScope: CoroutineScope) : ScopeModelService {
  override fun createScopeChooser(
    parentDisposable: Disposable,
    preselectedScopeName: String?,
    filterConditionType: ScopesFilterConditionType,
  ): FrontendScopeChooser {
    return FrontendScopeChooserImpl(project, preselectedScopeName, filterConditionType).also {
      Disposer.register(parentDisposable, it)
    }
  }
}