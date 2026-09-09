// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.newui

import com.intellij.ide.plugins.api.PluginDto
import com.intellij.openapi.extensions.PluginId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class UnifiedPluginRepositoryProjectionTest {
  @Test
  fun `physical installation targets receive only repository models available on their side`() {
    val local = plugin("local", PluginSource.LOCAL)
    val remote = plugin("remote", PluginSource.REMOTE)
    val both = plugin("both", PluginSource.BOTH)
    val plugins = listOf(local, remote, both)

    assertThat(filterCustomRepositoryPluginsForTarget(plugins, PluginSource.LOCAL)).containsExactly(local, both)
    assertThat(filterCustomRepositoryPluginsForTarget(plugins, PluginSource.REMOTE)).containsExactly(remote, both)
    assertThat(filterCustomRepositoryPluginsForTarget(plugins, PluginSource.BOTH)).containsExactly(local, remote, both)
  }

  @Test
  fun `unsettled repository projection remains absent`() {
    assertThat(filterCustomRepositoryPluginsForTarget(null, PluginSource.LOCAL)).isNull()
  }

  @Test
  fun `install uses the settled snapshot named by its plugin`() {
    val requested = plugin("requested", PluginSource.LOCAL, repositoryName = "target")
    val repositoryModel = plugin("requested", PluginSource.LOCAL)
    val dependency = plugin("dependency", PluginSource.BOTH)
    val other = plugin("other", PluginSource.LOCAL)
    val repositories = linkedMapOf(
      "target" to listOf(repositoryModel, dependency),
      "other" to listOf(other),
    )

    assertThat(selectCustomRepositoryPluginsForInstall(repositories, requested, PluginSource.LOCAL))
      .containsExactly(repositoryModel, dependency)
  }

  @Test
  fun `install uses the newest duplicate from its repository snapshot`() {
    val requested = plugin("requested", PluginSource.LOCAL, repositoryName = "target")
    val olderDependency = plugin("dependency", PluginSource.LOCAL, version = "1.0")
    val newerDependency = plugin("dependency", PluginSource.LOCAL, version = "2.0")
    val repositories = mapOf("target" to listOf(requested, olderDependency, newerDependency))

    assertThat(selectCustomRepositoryPluginsForInstall(repositories, requested, PluginSource.LOCAL))
      .containsExactly(requested, newerDependency)
  }

  @Test
  fun `install selects the newest duplicate available on its target`() {
    val requested = plugin("requested", PluginSource.REMOTE, repositoryName = "target")
    val newerLocalDependency = plugin("dependency", PluginSource.LOCAL, version = "2.0")
    val olderRemoteDependency = plugin("dependency", PluginSource.REMOTE, version = "1.0")
    val repositories = mapOf("target" to listOf(requested, newerLocalDependency, olderRemoteDependency))

    assertThat(selectCustomRepositoryPluginsForInstall(repositories, requested, PluginSource.REMOTE))
      .containsExactly(requested, olderRemoteDependency)
  }

  @Test
  fun `install without a repository uses all settled snapshots`() {
    val requested = plugin("requested", PluginSource.LOCAL)
    val firstDependency = plugin("first", PluginSource.LOCAL)
    val secondDependency = plugin("second", PluginSource.BOTH)
    val repositories = linkedMapOf(
      "first" to listOf(firstDependency),
      "second" to listOf(secondDependency),
    )

    assertThat(selectCustomRepositoryPluginsForInstall(repositories, requested, PluginSource.LOCAL))
      .containsExactly(firstDependency, secondDependency)
  }

  private fun plugin(
    id: String,
    source: PluginSource,
    version: String? = null,
    repositoryName: String? = null,
  ): PluginUiModel {
    return PluginDto(id, PluginId.getId(id)).apply {
      this.source = source
      this.version = version
      this.repositoryName = repositoryName
    }
  }
}
