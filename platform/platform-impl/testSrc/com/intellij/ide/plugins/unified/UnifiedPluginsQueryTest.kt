// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.unified

import com.intellij.ide.plugins.MarketplaceTabSearchSortByOptions
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class UnifiedPluginsQueryTest {
  @Test
  fun `parses compact and separated attributes plus installed filters`() {
    val query = UnifiedPluginsQuery.parse(
      "Kotlin /vendor:JetBrains /vendor: \"Acme Tools\" /category:\"Programming Language\" /tag:\"Developer Tools\" " +
      "/repository: \"https://plugins.example.test/list.xml\" /enabled /invalid"
    )

    assertThat(query.vendors).containsExactly("JetBrains", "Acme Tools")
    assertThat(query.categories).containsExactly("Programming Language")
    assertThat(query.tags).containsExactly("Developer Tools")
    assertThat(query.repositories).containsExactly("https://plugins.example.test/list.xml")
    assertThat(query.effectiveInstalledFilter).isEqualTo(UnifiedPluginInstalledFilter.Invalid)
    assertThat(query.hasPopupFilter).isTrue()
  }

  @Test
  fun `manual sort uses the last sort command and defaults to relevance`() {
    assertThat(UnifiedPluginsQuery.parse("Kotlin").effectiveSort)
      .isEqualTo(MarketplaceTabSearchSortByOptions.RELEVANCE)
    assertThat(UnifiedPluginsQuery.parse("/sortBy:downloads /sortBy:name").effectiveSort)
      .isEqualTo(MarketplaceTabSearchSortByOptions.NAME)
    assertThat(UnifiedPluginsQuery.parse("/sortBy:name /sortBy:unsupported").effectiveSort)
      .isEqualTo(MarketplaceTabSearchSortByOptions.RELEVANCE)
    assertThat(UnifiedPluginsQuery.parse("/sortBy:relevance").effectiveSort)
      .isEqualTo(MarketplaceTabSearchSortByOptions.RELEVANCE)
  }

  @Test
  fun `adding an attribute preserves free text unknown commands and unrelated repeated values`() {
    val query = UnifiedPluginsQuery.parse("Kotlin /unknown:keep /tag:Tools /tag:\"Developer Tools\"")

    assertThat(query.withAttribute(UnifiedPluginQueryAttribute.Vendor, "Acme, Inc.", selected = true))
      .isEqualTo("Kotlin /unknown:keep /tag:Tools /tag:\"Developer Tools\" /vendor:\"Acme, Inc.\"")
  }

  @Test
  fun `category attributes use the standard facet editing path`() {
    val query = UnifiedPluginsQuery.parse("Kotlin /category:Tools")

    assertThat(query.withAttribute(UnifiedPluginQueryAttribute.Category, "Programming Language", selected = true))
      .isEqualTo("Kotlin /category:Tools /category:\"Programming Language\"")
    assertThat(query.withAttribute(UnifiedPluginQueryAttribute.Category, "Tools", selected = false))
      .isEqualTo("Kotlin")
  }

  @Test
  fun `removing an attribute removes every exact occurrence only`() {
    val query = UnifiedPluginsQuery.parse("Kotlin /vendor:JetBrains /vendor:Acme /vendor:JetBrains /enabled")

    assertThat(query.withAttribute(UnifiedPluginQueryAttribute.Vendor, "JetBrains", selected = false))
      .isEqualTo("Kotlin /vendor:Acme /enabled")
  }

  @Test
  fun `facet values ignore surrounding whitespace while raw tokens remain lossless`() {
    val query = UnifiedPluginsQuery.parse("Kotlin /vendor:\" JetBrains \" /tag:\"  \"")

    assertThat(query.vendors).containsExactly("JetBrains")
    assertThat(query.tags).isEmpty()
    assertThat(query.withAttribute(UnifiedPluginQueryAttribute.Vendor, "JetBrains", selected = false))
      .isEqualTo("Kotlin /tag:\"  \"")
  }

  @Test
  fun `installed filter selection canonicalizes its exclusive command group`() {
    val query = UnifiedPluginsQuery.parse("Kotlin /enabled /unknown")

    assertThat(query.withInstalledFilter(UnifiedPluginInstalledFilter.Enabled, selected = true))
      .isEqualTo(query.rawQuery)
    assertThat(query.withInstalledFilter(UnifiedPluginInstalledFilter.Enabled, selected = false))
      .isEqualTo("Kotlin /unknown")
    assertThat(query.withInstalledFilter(UnifiedPluginInstalledFilter.Disabled, selected = true))
      .isEqualTo("Kotlin /unknown /disabled")

    val conflicting = UnifiedPluginsQuery.parse("Kotlin /enabled /invalid /enabled")
    assertThat(conflicting.effectiveInstalledFilter).isEqualTo(UnifiedPluginInstalledFilter.Enabled)
    assertThat(conflicting.withInstalledFilter(UnifiedPluginInstalledFilter.Enabled, selected = true))
      .isEqualTo("Kotlin /enabled")
  }

  @Test
  fun `selecting sort canonicalizes supported sort commands and preserves unsupported ones`() {
    val query = UnifiedPluginsQuery.parse("Kotlin /sortBy:downloads /sortBy:unsupported /sortBy:rating")

    assertThat(query.withSort(MarketplaceTabSearchSortByOptions.NAME))
      .isEqualTo("Kotlin /sortBy:unsupported /sortBy:name")
    assertThat(query.withSort(MarketplaceTabSearchSortByOptions.RELEVANCE))
      .isEqualTo("Kotlin /sortBy:unsupported")
  }

  @Test
  fun `render exclusion retains original token content while normalizing boundaries`() {
    val query = UnifiedPluginsQuery.parse("  Kotlin   /vendor: \"Acme Tools\"   /sortBy:name ")

    assertThat(query.renderExcluding { part ->
      part is UnifiedPluginQueryPart.Attribute && part.attribute == UnifiedPluginQueryAttribute.Sort
    }).isEqualTo("Kotlin /vendor: \"Acme Tools\"")
  }

  @Test
  fun `unified source route strips source-owned controls from shared projections`() {
    val route = PluginsQueryState(
      rawQuery = "Kotlin /vendor:JetBrains /sortBy:rating",
      normalizedQuery = "Kotlin /vendor:JetBrains /sortBy:rating",
    ).sourceRoute()

    assertThat(route.local).isEqualTo(UnifiedPluginSourceProjection(true, "Kotlin /vendor:JetBrains"))
    assertThat(route.internal).isEqualTo(UnifiedPluginSourceProjection(true, "Kotlin /vendor:JetBrains"))
    assertThat(route.marketplace).isEqualTo(UnifiedPluginSourceProjection(true, "Kotlin /vendor:JetBrains /sortBy:rating"))
    assertThat(route.repositories).isEqualTo(UnifiedPluginSourceProjection(true, "Kotlin /vendor:JetBrains"))
    assertThat(route.marketplaceMode).isEqualTo(UnifiedPluginMarketplaceSourceMode.Search)
    assertThat(route.sortVisible).isTrue()
  }

  @Test
  fun `internal query targets only the internal source`() {
    val route = PluginsQueryState(
      rawQuery = "/internal Kotlin /vendor:JetBrains /sortBy:name",
      normalizedQuery = "/internal Kotlin /vendor:JetBrains /sortBy:name",
    ).sourceRoute()

    assertThat(route.local.eligible).isFalse()
    assertThat(route.internal).isEqualTo(UnifiedPluginSourceProjection(true, "Kotlin /vendor:JetBrains"))
    assertThat(route.marketplace.eligible).isFalse()
    assertThat(route.repositories.eligible).isFalse()
    assertThat(route.marketplaceMode).isEqualTo(UnifiedPluginMarketplaceSourceMode.Inactive)
    assertThat(route.sortVisible).isTrue()
  }

  @Test
  fun `unified route makes installed and repository constraints mutually exclusive`() {
    val installed = PluginsQueryState("Kotlin /disabled", "Kotlin /disabled").sourceRoute()
    assertThat(installed.local.eligible).isTrue()
    assertThat(installed.marketplace.eligible).isFalse()
    assertThat(installed.repositories.eligible).isFalse()
    assertThat(installed.sortVisible).isTrue()

    val repository = PluginsQueryState(
      "/repository:first Kotlin /sortBy:name",
      "/repository:first Kotlin /sortBy:name",
    ).sourceRoute()
    assertThat(repository.local.eligible).isFalse()
    assertThat(repository.marketplace.eligible).isFalse()
    assertThat(repository.repositories).isEqualTo(UnifiedPluginSourceProjection(true, "Kotlin"))
    assertThat(repository.selectedRepositoryIds).containsExactly("first")
    assertThat(repository.sortVisible).isFalse()

    val conflicting = PluginsQueryState("/disabled /repository:first", "/disabled /repository:first").sourceRoute()
    assertThat(conflicting.local.eligible).isFalse()
    assertThat(conflicting.marketplace.eligible).isFalse()
    assertThat(conflicting.repositories.eligible).isFalse()
    assertThat(conflicting.sortVisible).isFalse()
  }

  @Test
  fun `local projection applies only the last manually entered installed filter`() {
    val route = PluginsQueryState(
      "Kotlin /enabled /invalid /disabled",
      "Kotlin /enabled /invalid /disabled",
    ).sourceRoute()
    assertThat(route.local.query).isEqualTo("Kotlin /disabled")

    val installed = PluginsQueryState(
      "Kotlin /enabled /invalid /disabled",
      "Kotlin /enabled /invalid /disabled",
      scope = PluginsQueryScope.Installed,
    ).sourceRoute()
    assertThat(installed.local.query).isEqualTo("Kotlin /disabled")
    assertThat(installed.sortVisible).isTrue()
  }
}
