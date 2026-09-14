// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit2.configuration

import com.intellij.execution.JUnitBundle
import com.intellij.openapi.components.serviceOrNull
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

@ApiStatus.Internal
interface JUnitTestDiscoveryProvider {
  fun getChangeListNames(project: Project): List<@Nls String>

  companion object {
    @JvmStatic
    fun getInstance(): JUnitTestDiscoveryProvider = serviceOrNull<JUnitTestDiscoveryProvider>() ?: DefaultJUnitTestDiscoveryProvider
  }
}

private object DefaultJUnitTestDiscoveryProvider : JUnitTestDiscoveryProvider {
  override fun getChangeListNames(project: Project): List<@Nls String> {
    return listOf(JUnitBundle.message("test.discovery.by.all.changes.combo.item"))
  }
}
