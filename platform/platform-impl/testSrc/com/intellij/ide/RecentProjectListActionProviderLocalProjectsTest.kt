// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide

import com.intellij.openapi.Disposable
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.util.registry.Registry
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
internal class RecentProjectListActionProviderLocalProjectsTest {
  @TestDisposable
  private lateinit var disposable: Disposable

  @Test
  fun `the local recent projects do not ask the providers`() {
    val provider = CountingRecentProjectProvider()
    ExtensionTestUtil.maskExtensions(ExtensionPointName<RecentProjectProvider>("com.intellij.recentProjectsProvider"),
                                     listOf(provider), disposable, fireEvents = false)

    RecentProjectListActionProvider.getInstance().collectLocalProjects()
    assertThat(provider.requestCount).isZero()

    RecentProjectListActionProvider.getInstance().collectProjects()
    assertThat(provider.requestCount).isEqualTo(if (Registry.`is`("ide.recent.projects.query.ep.providers")) 1 else 0)
  }
}

private class CountingRecentProjectProvider : RecentProjectProvider {
  var requestCount: Int = 0

  override val providerId: String
    get() = "RecentProjectListActionProviderLocalProjectsTest"

  override fun getRecentProjects(): List<RecentProject> {
    requestCount++
    return emptyList()
  }
}
