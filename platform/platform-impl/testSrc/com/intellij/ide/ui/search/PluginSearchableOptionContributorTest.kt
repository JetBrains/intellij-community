// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.search

import com.intellij.ide.IdeBundle
import com.intellij.ide.actions.ShowSettingsUtilImpl
import com.intellij.ide.plugins.PluginManager
import com.intellij.ide.plugins.PluginManagerConfigurable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.gotoByName.FindActionSearchableOptionsFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import javax.swing.event.DocumentEvent

@TestApplication
internal class PluginSearchableOptionContributorTest {
  @Test
  fun `Settings search finds Plugins by installed plugin name and description`(): Unit = timeoutRunBlocking {
    val registrar = serviceAsync<SearchableOptionsRegistrar>() as SearchableOptionsRegistrarImpl
    registrar.initialize()
    val (plugin, descriptionWord) = PluginManager.getVisiblePlugins(false)
      .map { descriptor ->
        descriptor to registrar.getProcessedWordsWithoutStemming(descriptor.description.orEmpty())
          .firstOrNull { it.length >= 8 && it.all(Char::isLetter) && !descriptor.name.contains(it, ignoreCase = true) }
      }
      .filter { (descriptor, word) ->
        descriptor.name.length > 5 && !descriptor.name.equals(IdeBundle.message("title.plugins"), ignoreCase = true) && word != null
      }
      .findFirst()
      .orElseThrow()

    val descriptionTerm = requireNotNull(descriptionWord)
    val groups = ShowSettingsUtilImpl.getConfigurableGroups(null, true).toList()
    for (query in listOf(plugin.name, descriptionTerm)) {
      val hits = withContext(Dispatchers.EDT) {
        registrar.getConfigurables(groups, DocumentEvent.EventType.INSERT, null, query, null)
      }
      assertThat(hits.contentHits).anyMatch { it is SearchableConfigurable && it.id == PluginManagerConfigurable.ID }
    }

    val pluginOptions = (registrar.getProcessedWordsWithoutStemming(plugin.name) + descriptionTerm)
      .flatMap { registrar.findAcceptableDescriptions(it)?.toList().orEmpty() }
      .filter { it.configurableId == PluginManagerConfigurable.ID && it.hit == plugin.name }
    assertThat(pluginOptions).isNotEmpty()
    assertThat(pluginOptions).allMatch { option ->
      FindActionSearchableOptionsFilter.EP_NAME.extensionList.any { !it.isAvailable(option) }
    }
  }
}
