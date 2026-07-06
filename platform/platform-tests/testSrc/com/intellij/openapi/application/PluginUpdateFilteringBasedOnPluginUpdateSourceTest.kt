// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application

import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.updateSettings.impl.PluginUpdateSourceId
import com.intellij.openapi.updateSettings.impl.PluginUpdateSourceService
import com.intellij.openapi.updateSettings.impl.UpdateCheckerFacade
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.http.url
import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@TestApplication
@RegistryKey(key = "platform.enable.plugin.update.source.feature", value = "true")
@RegistryKey(key = "platform.limit.plugin.update.source.by.configured.one", value = "true")
internal class PluginUpdateFilteringBasedOnPluginUpdateSourceTest : UpdateCheckerTestBase() {
  private val updateSourcePluginIds = mutableSetOf<PluginId>()

  @AfterEach
  fun cleanUpPluginUpdateSources() {
    for (pluginId in updateSourcePluginIds) {
      PluginUpdateSourceService.getInstance().erasePluginUpdateSourceId(pluginId)
    }
    updateSourcePluginIds.clear()
  }

  @Test
  fun `plugin updates are checked only against recorded plugin update source`() {
    val customServer = createTestServer(testDisposable.get())
    val customRepositoryUrl = customServer.url + "/custom-repository"

    val installedPluginIds = listOf(
      MARKETPLACE_SOURCE_BOTH,
      MARKETPLACE_SOURCE_CUSTOM_ONLY,
      MARKETPLACE_SOURCE_MARKETPLACE_ONLY,
      CUSTOM_SOURCE_BOTH,
      CUSTOM_SOURCE_MARKETPLACE_ONLY,
      CUSTOM_SOURCE_CUSTOM_ONLY,
      WITHOUT_SOURCE_BOTH,
    )
    installedPluginsFacade.setPlugins(installedPluginIds.map { InstalledPluginMock(it, it, "JetBrains", "1.0", "1.0", "999.99999", true) })
    installedPluginsFacade.setHosts(listOf(customRepositoryUrl))

    val marketplaceUpdates = listOf(
      RepositoryPluginMock(MARKETPLACE_SOURCE_BOTH, "501", "101", "2.0"),
      RepositoryPluginMock(MARKETPLACE_SOURCE_MARKETPLACE_ONLY, "502", "102", "7.0"),
      RepositoryPluginMock(CUSTOM_SOURCE_BOTH, "503", "103", "9.0"),
      RepositoryPluginMock(CUSTOM_SOURCE_MARKETPLACE_ONLY, "504", "104", "9.0"),
      RepositoryPluginMock(WITHOUT_SOURCE_BOTH, "505", "105", "9.0"),
    )
    setMarketplacePlugins(marketplaceUpdates, installedPluginIds)

    setCustomRepositoryPlugins(customServer, listOf(
      CustomRepositoryPlugin(MARKETPLACE_SOURCE_BOTH, "9.0"),
      CustomRepositoryPlugin(MARKETPLACE_SOURCE_CUSTOM_ONLY, "9.0"),
      CustomRepositoryPlugin(CUSTOM_SOURCE_BOTH, "3.0"),
      CustomRepositoryPlugin(CUSTOM_SOURCE_CUSTOM_ONLY, "6.0"),
      CustomRepositoryPlugin(WITHOUT_SOURCE_BOTH, "10.0"),
    ))

    setPluginUpdateSources(PluginUpdateSourceService.getInstance().createMarketplacePluginUpdateSourceId(),
                           MARKETPLACE_SOURCE_BOTH,
                           MARKETPLACE_SOURCE_CUSTOM_ONLY,
                           MARKETPLACE_SOURCE_MARKETPLACE_ONLY)
    setPluginUpdateSources(PluginUpdateSourceService.getInstance().createCustomRepositoryPluginUpdateSourceId(customRepositoryUrl),
                           CUSTOM_SOURCE_BOTH,
                           CUSTOM_SOURCE_MARKETPLACE_ONLY,
                           CUSTOM_SOURCE_CUSTOM_ONLY)

    val internalResult = UpdateCheckerFacade.getInstance().getPluginUpdates(installedPluginIds.map { PluginId.getId(it) })
    assertEquals(emptyMap<String?, Exception>(), internalResult.errors)
    val result = internalResult.pluginUpdates

    val updatesById = result.allEnabled.associateBy { it.id.idString }
    assertEquals(setOf(MARKETPLACE_SOURCE_BOTH, MARKETPLACE_SOURCE_MARKETPLACE_ONLY, CUSTOM_SOURCE_BOTH, CUSTOM_SOURCE_CUSTOM_ONLY),
                 updatesById.keys)
    assertEquals("2.0", updatesById.getValue(MARKETPLACE_SOURCE_BOTH).pluginVersion)
    assertEquals("7.0", updatesById.getValue(MARKETPLACE_SOURCE_MARKETPLACE_ONLY).pluginVersion)
    assertEquals("3.0", updatesById.getValue(CUSTOM_SOURCE_BOTH).pluginVersion)
    assertEquals("6.0", updatesById.getValue(CUSTOM_SOURCE_CUSTOM_ONLY).pluginVersion)
    assertTrue(result.allDisabled.isEmpty())

    val updatedIds = result.all.map { it.id.idString }.toSet()
    assertFalse(MARKETPLACE_SOURCE_CUSTOM_ONLY in updatedIds)
    assertFalse(CUSTOM_SOURCE_MARKETPLACE_ONLY in updatedIds)
    assertFalse(WITHOUT_SOURCE_BOTH in updatedIds)

    val receivedMarketplaceRequestUri = receivedUpdatesRequestUri
    assertNotNull(receivedMarketplaceRequestUri)
    val marketplaceRequestedIds = getPluginIdsFromQuery(receivedMarketplaceRequestUri).toSet()
    assertEquals(setOf(MARKETPLACE_SOURCE_BOTH, MARKETPLACE_SOURCE_CUSTOM_ONLY, MARKETPLACE_SOURCE_MARKETPLACE_ONLY,
                       CUSTOM_SOURCE_BOTH, CUSTOM_SOURCE_CUSTOM_ONLY, CUSTOM_SOURCE_MARKETPLACE_ONLY, WITHOUT_SOURCE_BOTH),
                 marketplaceRequestedIds)
  }

  private fun setMarketplacePlugins(plugins: List<RepositoryPluginMock>, knownPluginIds: Collection<String>) {
    server.createContext("/plugins/files/pluginsXMLIds.json") { handler ->
      handler.sendResponseHeaders(200, 0)
      handler.responseBody.writer().use { out ->
        out.write(objectMapper.writeValueAsString(knownPluginIds))
      }
    }

    for ((pluginId, externalPluginId, externalUpdateId, version) in plugins) {
      server.createContext("/plugins/files/$externalPluginId/$externalUpdateId/meta.json") { handler ->
        handler.sendResponseHeaders(200, 0)
        handler.responseBody.writer().use {
          it.write(getMetaJson(pluginId, externalPluginId, version, "1.0", "999.9999"))
        }
      }
    }

    server.createContext("/plugins/api/search/updates/compatible") { handler ->
      receivedUpdatesRequestUri = handler.requestURI
      val requestedPluginIds = getPluginIdsFromQuery(handler.requestURI).toSet()
      val updates = plugins.filter { it.pluginId in requestedPluginIds }
      handler.sendResponseHeaders(200, 0)
      handler.responseBody.writer().use {
        it.write(getUpdatesResponseJson(updates))
      }
    }
  }

  private fun setCustomRepositoryPlugins(customServer: HttpServer, plugins: List<CustomRepositoryPlugin>) {
    customServer.createContext("/custom-repository") { handler ->
      handler.sendResponseHeaders(200, 0)
      handler.responseBody.writer().use { out ->
        out.write(
          """
          <plugins>
            ${plugins.joinToString("\n") { it.toPluginXml(customServer) }}
          </plugins>
          """.trimIndent())
      }
    }

    for (plugin in plugins) {
      customServer.createContext(plugin.downloadPath) { handler ->
        handler.sendResponseHeaders(200, 0)
        handler.responseBody.use { output ->
          JarOutputStream(output).use { jarOutput ->
            jarOutput.putNextEntry(ZipEntry("META-INF/plugin.xml"))
            jarOutput.write(plugin.toJarPluginXml().toByteArray())
          }
        }
      }
    }
  }

  private data class CustomRepositoryPlugin(val pluginId: String, val version: String) {
    val downloadPath: String = "/downloads/$pluginId-$version.jar"

    fun toPluginXml(customServer: HttpServer): String {
      return """
            <plugin id="$pluginId">
              <name>$pluginId</name>
              <description>$pluginId plugin</description>
              <version>$version</version>
              <vendor>JetBrains</vendor>
              <idea-version since-build="1.0" until-build="999.*"/>
              <change-notes>Update</change-notes>
              <download-url>${customServer.url}$downloadPath</download-url>
            </plugin>
      """.trimIndent()
    }

    fun toJarPluginXml(): String {
      return """
        <idea-plugin>
          <id>$pluginId</id>
          <name>$pluginId</name>
          <description>$pluginId plugin</description>
          <vendor>JetBrains</vendor>
          <version>$version</version>
          <idea-version since-build="1.0" until-build="999.9999"/>
        </idea-plugin>
      """.trimIndent()
    }
  }

  private fun setPluginUpdateSources(pluginUpdateSourceId: PluginUpdateSourceId, vararg pluginIds: String) {
    pluginIds.forEach { pluginId ->
      val id = PluginId.getId(pluginId)
      PluginUpdateSourceService.getInstance().setPluginUpdateSourceId(id, pluginUpdateSourceId)
      updateSourcePluginIds.add(id)
    }
  }

  private companion object {
    const val MARKETPLACE_SOURCE_BOTH = "test.marketplace.source.both"
    const val MARKETPLACE_SOURCE_MARKETPLACE_ONLY = "test.marketplace.marketplace.only"
    const val MARKETPLACE_SOURCE_CUSTOM_ONLY = "test.marketplace.source.custom.only"
    const val CUSTOM_SOURCE_BOTH = "test.custom.source.both"
    const val CUSTOM_SOURCE_MARKETPLACE_ONLY = "test.custom.source.marketplace.only"
    const val CUSTOM_SOURCE_CUSTOM_ONLY = "test.custom.source.custom.only"
    const val WITHOUT_SOURCE_BOTH = "test.without.source.both"
  }
}
