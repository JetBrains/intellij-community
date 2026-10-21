// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.testPluginSrc.customScopes

import com.intellij.icons.AllIcons
import com.intellij.psi.search.scope.packageSet.CustomScopesProvider
import com.intellij.psi.search.scope.packageSet.FilePatternPackageSet
import com.intellij.psi.search.scope.packageSet.NamedScope

internal const val MY_CUSTOM_SCOPE_ID: String = "dynamic.plugins.test.scope"

/**
 * The presentable name supplier of the scope is a lambda of the plugin, so a cached scope keeps the plugin class loader.
 */
internal class MyCustomScopesProvider : CustomScopesProvider {
  private val scopes = listOf(
    NamedScope(MY_CUSTOM_SCOPE_ID, { "Dynamic plugins test scope" }, AllIcons.Ide.LocalScope, FilePatternPackageSet(null, "*.txt")),
  )

  override fun getCustomScopes(): List<NamedScope> = scopes
}
