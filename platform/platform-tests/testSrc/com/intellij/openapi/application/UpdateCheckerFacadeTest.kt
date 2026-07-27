// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(LowLevelLocalMachineAccess::class)

package com.intellij.openapi.application

import com.intellij.ide.plugins.marketplace.utils.MarketplaceCustomizationService
import com.intellij.internal.statistic.eventLog.fus.MachineIdManager
import com.intellij.openapi.application.impl.ApplicationInfoImpl
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.updateSettings.PluginUpdateCheckService
import com.intellij.openapi.updateSettings.PluginUpdateInfo
import com.intellij.openapi.updateSettings.impl.UpdateCheckerFacade
import com.intellij.openapi.util.BuildNumber
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.http.url
import com.intellij.testFramework.replaceService
import com.intellij.util.application
import com.intellij.util.io.HttpRequests
import com.intellij.util.queryParameters
import com.intellij.util.system.CpuArch
import com.intellij.util.system.LowLevelLocalMachineAccess
import com.intellij.util.system.OS
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import java.net.URI
import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@TestApplication
@RegistryKey(key = "platform.enable.plugin.update.source.feature", value = "false")
internal class UpdateCheckerFacadeTest : UpdateCheckerTestBase() {

  @Test
  fun `no plugins have no updates`() {
    installedPluginsFacade.setPlugins(emptyList())

    val result = UpdateCheckerFacade.getInstance().checkInstalledPluginUpdates()
    assertEquals(0, result.pluginUpdates.all.size, "There must be no updates found for no plugins")
  }

  @Test
  fun `downgrade for broken plugin`() {
    installedPluginsFacade.setPlugins(listOf(
      InstalledPluginMock("ColourChooser", "Colour Chooser", "JetBrains", "0.2", "0", "999.99999", true),
      InstalledPluginMock("ImageView", "Image View", "JetBrains", "0.1", "1.0", "999.99999", true),
    ))

    val serverPlugins = listOf(
      RepositoryPluginMock("ColourChooser", "501", "101", "0.1"),
      RepositoryPluginMock("ImageView", "502", "102", "0.5"),
    )
    setServerPlugins(serverPlugins, serverPlugins)

    server.removeContext("/plugins/files/brokenPlugins.json")
    server.createContext("/plugins/files/brokenPlugins.json") { handler ->
      handler.sendResponseHeaders(200, 0)
      handler.responseBody.writer().use {
        it.write( // language=JSON
          """
          [{"id":"ColourChooser","version":"0.2","since":"0","until":"1.0","originalSince":"1.0","originalUntil":"999.99999"}]
          """.trimIndent()
        )
      }
    }

    val result = UpdateCheckerFacade.getInstance().checkInstalledPluginUpdates().pluginUpdates
    assertEquals(2, result.all.size)

    val brokenPluginUpdate = result.all.find { it.id.idString == "ColourChooser" }
    assertNotNull(brokenPluginUpdate, "There must be downgrade version for ColourChooser plugin")
    assertEquals("0.1", brokenPluginUpdate.pluginVersion)
  }

  @Test
  fun `update check sends all query parameters`() {
    installedPluginsFacade.setPlugins(listOf(
      InstalledPluginMock("ColourChooser", "Colour Chooser", "JetBrains", "0.1", "0", "999.99999", true),
      InstalledPluginMock("ImageView", "Image View", "JetBrains", "0.1", "1.0", "999.99999", true),
    ))

    val serverPlugins = listOf(
      RepositoryPluginMock("ColourChooser", "501", "101", "0.1"),
      RepositoryPluginMock("ImageView", "502", "102", "0.1"),
    )
    setServerPlugins(serverPlugins, emptyList())

    val result = UpdateCheckerFacade.getInstance().checkInstalledPluginUpdates().pluginUpdates
    assertTrue(result.all.isEmpty())

    assertNotNull(receivedUpdatesRequestUri, "There must be a request from the IDE")

    val queryParameters = receivedUpdatesRequestUri!!.queryParameters
    assertTrue(queryParameters.contains("mid"), "Query parameters must contain 'mid'")
    assertEquals(MachineIdManager.getAnonymizedMachineId("JetBrainsUpdates"), queryParameters["mid"])

    assertTrue(queryParameters.contains("build"), "Query parameters must contain 'build'")
    assertEquals(ApplicationInfoImpl.getShadowInstanceImpl().getPluginCompatibleBuildAsNumber().asString(),
                 queryParameters["build"])

    assertTrue(queryParameters.contains("os"), "Query parameters must contain 'os'")
    assertEquals("${OS.CURRENT}+${OS.CURRENT.version()}", queryParameters["os"])

    assertTrue(queryParameters.contains("arch"), "Query parameters must contain 'arch'")
    assertEquals(CpuArch.CURRENT.name, queryParameters["arch"])

    // Java URI does not support repeatable names for pluginXmlId
    val pluginIds = getPluginIdsFromQuery(receivedUpdatesRequestUri)

    assertEquals(2, pluginIds.size)
    assertTrue(pluginIds.contains("ImageView"))
    assertTrue(pluginIds.contains("ColourChooser"))
  }

  @Test
  fun `update check of marketplace-like repository has no mid in query`() {
    application.replaceService(MarketplaceCustomizationService::class.java, TestMarketplaceCustomizationService(server.url, false),
                               testDisposable.get())

    installedPluginsFacade.setPlugins(listOf(
      InstalledPluginMock("ColourChooser", "Colour Chooser", "JetBrains", "0.1", "0", "999.99999", true),
      InstalledPluginMock("ImageView", "Image View", "JetBrains", "0.1", "1.0", "999.99999", true),
    ))

    val serverPlugins = listOf(
      RepositoryPluginMock("ColourChooser", "501", "101", "0.1"),
      RepositoryPluginMock("ImageView", "502", "102", "0.1"),
    )
    setServerPlugins(serverPlugins, emptyList())

    val result = UpdateCheckerFacade.getInstance().checkInstalledPluginUpdates().pluginUpdates
    assertTrue(result.all.isEmpty())

    assertNotNull(receivedUpdatesRequestUri, "There must be a request from the IDE")
    val queryParameters = receivedUpdatesRequestUri!!.queryParameters

    assertFalse(queryParameters.contains("mid"), "Query parameters must not contain 'mid'")

    assertTrue(queryParameters.contains("build"), "Query parameters must contain 'build'")
    assertEquals(ApplicationInfoImpl.getShadowInstanceImpl().getPluginCompatibleBuildAsNumber().asString(),
                 queryParameters["build"])
  }

  @Test
  fun `not installed yet plugin`() {
    installedPluginsFacade.setPlugins(listOf(
      InstalledPluginMock("ColourChooser", "Colour Chooser", "JetBrains", "1.0", "0", "999.99999", true),
      // no ImageView installed, but we ask for its versions
    ))

    val serverPlugins = listOf(
      RepositoryPluginMock("ColourChooser", "501", "101", "2.0"),
      RepositoryPluginMock("ImageView", "502", "102", "2.1"),
    )
    setServerPlugins(serverPlugins, serverPlugins)

    val result = UpdateCheckerFacade.getInstance().getPluginUpdates(
      plugins = listOf(PluginId.getId("ColourChooser"), PluginId.getId("ImageView"))
    ).pluginUpdates

    assertEquals(2, result.allEnabled.size)
    val ids = result.allEnabled.map { it.id }
    assertTrue(ids.contains(PluginId.getId("ColourChooser")))
    assertTrue(ids.contains(PluginId.getId("ImageView")))
  }

  @Test
  fun `compatible with new build number`() {
    installedPluginsFacade.setPlugins(listOf(
      InstalledPluginMock("ColourChooser", "Colour Chooser", "JetBrains", "1.0", "0", "999.99999", true),
      InstalledPluginMock("ImageView", "Image View", "JetBrains", "0.1", "1.0", "999.99999", true),
    ))

    val serverPlugins = listOf(
      // incompatible with build number > 251.250
      RepositoryPluginMock(
        "ColourChooser", "501", "101", "2.0",
        getMetaJson("ColourChooser", "501", "2.0", "251.200", "251.250")
      ),
      RepositoryPluginMock(
        "ImageView", "502", "102", "2.1",
        getMetaJson("ImageView", "502", "2.1", "251.100", "251.450")
      ),
    )
    setServerPlugins(serverPlugins, serverPlugins)

    val result = UpdateCheckerFacade.getInstance().checkInstalledPluginUpdates(
      buildNumber = BuildNumber.fromString("251.300")
    ).pluginUpdates

    assertEquals(1, result.all.size)
    val update = result.all[0]
    assertEquals("ImageView", update.id.idString)
  }

  @Test
  fun `plugins become incompatible with new build number`() {
    installedPluginsFacade.setPlugins(listOf(
      InstalledPluginMock("ColourChooser", "Colour Chooser", "JetBrains", "1.0", "251.200", "251.250", true),
      InstalledPluginMock("ImageView", "Image View", "JetBrains", "0.1", "1.0", "999.99999", true),
    ))

    val colourChooserPlugin = RepositoryPluginMock(
      "ColourChooser", "501", "101", "1.0",
      getMetaJson("ColourChooser", "501", "2.0", "251.200", "251.250")
    )
    val imageViewPlugin = RepositoryPluginMock(
      "ImageView", "502", "102", "2.1",
      getMetaJson("ImageView", "502", "2.1", "251.100", "251.400")
    )
    setServerPlugins(
      listOf(
        // incompatible with build number > 251.250
        colourChooserPlugin,
        imageViewPlugin,
      ),
      listOf(imageViewPlugin)
    )

    val result = UpdateCheckerFacade.getInstance().checkInstalledPluginUpdates(
      buildNumber = BuildNumber.fromString("251.300")
    ).pluginUpdates

    assertEquals(1, result.all.size)
    assertEquals("ImageView", result.allEnabled.first().id.idString)
    assertEquals(1, result.incompatible.size)
    assertEquals("ColourChooser", result.incompatible.first().pluginId.idString)
  }

  @Test
  fun `custom repository`() {
    installedPluginsFacade.setPlugins(listOf(
      InstalledPluginMock("ColourChooser", "Colour Chooser", "JetBrains", "1.0", "0", "999.99999", true),
      InstalledPluginMock("ImageView", "Image View", "JetBrains", "0.1", "1.0", "999.99999", true),
    ))

    val serverPlugins = listOf(
      RepositoryPluginMock("ColourChooser", "501", "101", "2.0"),
    )
    setServerPlugins(serverPlugins, serverPlugins)

    installedPluginsFacade.setHosts(listOf(server.url + "/custom-repository"))

    server.createContext("/custom-repository") { handler ->
      handler.sendResponseHeaders(200, 0)
      handler.responseBody.writer().use { out ->
        out.write(
          // language=XML
          """
          <?xml version="1.0" encoding="UTF-8"?>
          <plugins>
            <plugin id="ImageView">
              <name>ImageView</name>
              <description>Lightweight image viewer</description>
              <version>2.2</version>
              <vendor email="dev@example.com" url="https://example.com">Example Corp</vendor>
              <idea-version since-build="251.0" until-build="999.*"/>
              <change-notes>Initial release</change-notes>
              <download-url>${server.url}/downloads/imageview-2.2.jar</download-url>
            </plugin>
            <plugin id="ImageView">
              <name>ImageView</name>
              <description>Lightweight image viewer</description>
              <version>2.5</version>
              <vendor email="dev@example.com" url="https://example.com">Example Corp</vendor>
              <idea-version since-build="999.0" until-build="999.*"/>
              <change-notes>Update</change-notes>
              <download-url>${server.url}/downloads/imageview-2.5.jar</download-url>
            </plugin>
          </plugins>
        """.trimIndent())
      }
    }

    server.createContext("/downloads/imageview-2.2.jar") { handler ->
      handler.sendResponseHeaders(200, 0)
      // scary things, custom repositories download plugin binaries on check of updates !
      handler.responseBody.use { output ->
        val jarOutput = JarOutputStream(output)
        jarOutput.putNextEntry(ZipEntry("META-INF/plugin.xml"))
        jarOutput.write(
          // language=XML
          """
            <idea-plugin>
              <id>ImageView</id>
              <name>ImageView</name>
              <description>Lightweight image viewer</description>
              <vendor email="dev@example.com" url="https://example.com">Example Corp</vendor>
              <version>2.2</version>
              <idea-version since-build="1.0" until-build="999.9999"/>
            </idea-plugin>
          """.trimIndent().toByteArray())
        jarOutput.finish()
      }
    }

    val result = UpdateCheckerFacade.getInstance().checkInstalledPluginUpdates().pluginUpdates

    assertEquals(2, result.allEnabled.size)
    val ids = result.allEnabled.map { it.id }
    assertTrue(ids.contains(PluginId.getId("ColourChooser")))
    assertTrue(ids.contains(PluginId.getId("ImageView")))

    assertEquals("2.2", result.allEnabled.find { it.id == PluginId.getId("ImageView") }!!.pluginVersion)
    assertEquals("2.0", result.allEnabled.find { it.id == PluginId.getId("ColourChooser") }!!.pluginVersion)
  }

  @Test
  fun `custom repository receives os and arch in query`() {
    installedPluginsFacade.setPlugins(listOf(
      InstalledPluginMock("ImageView", "Image View", "JetBrains", "0.1", "1.0", "999.99999", true),
    ))

    setServerPlugins(emptyList(), emptyList())

    installedPluginsFacade.setHosts(listOf(server.url + "/custom-repository"))

    var capturedUri: URI? = null
    server.createContext("/custom-repository") { handler ->
      capturedUri = handler.requestURI
      handler.sendResponseHeaders(200, 0)
      handler.responseBody.writer().use { out ->
        out.write("""<?xml version="1.0" encoding="UTF-8"?><plugins/>""")
      }
    }

    UpdateCheckerFacade.getInstance().checkInstalledPluginUpdates()

    assertNotNull(capturedUri, "Custom repository must receive a request")
    val params = capturedUri.queryParameters

    assertTrue(params.contains("build"), "Query parameters must contain 'build'")
    assertEquals(ApplicationInfoImpl.getShadowInstanceImpl().getPluginCompatibleBuildAsNumber().asString(),
                 params["build"])

    assertTrue(params.contains("os"), "Query parameters must contain 'os'")
    assertEquals("${OS.CURRENT} ${OS.CURRENT.version()}", params["os"])

    assertTrue(params.contains("arch"), "Query parameters must contain 'arch'")
    assertEquals(CpuArch.CURRENT.name, params["arch"])
  }

  @Test
  fun `disabled plugin`() {
    installedPluginsFacade.setPlugins(listOf(
      InstalledPluginMock("ColourChooser", "Colour Chooser", "JetBrains", "1.0", "0", "999.99999", true),
      InstalledPluginMock("ImageView", "Image View", "JetBrains", "0.1", "1.0", "999.99999", false),
    ))

    val serverPlugins = listOf(
      RepositoryPluginMock("ColourChooser", "501", "101", "2.0"),
      RepositoryPluginMock("ImageView", "502", "102", "2.1"),
    )
    setServerPlugins(serverPlugins, serverPlugins)

    val result = UpdateCheckerFacade.getInstance().checkInstalledPluginUpdates(
      buildNumber = BuildNumber.fromString("251.300")
    ).pluginUpdates

    assertEquals(1, result.allEnabled.size)
    assertEquals(1, result.allDisabled.size)
    assertEquals("ColourChooser", result.allEnabled.first().id.idString)
    assertEquals("ImageView", result.allDisabled.first().id.idString)
  }

  @Test
  fun `server errors`() {
    installedPluginsFacade.setPlugins(listOf(
      InstalledPluginMock("ColourChooser", "Colour Chooser", "JetBrains", "1.0", "0", "999.99999", true)
    ))

    val serverPlugins = listOf(RepositoryPluginMock("ColourChooser", "501", "101", "1.0"))
    setServerPlugins(serverPlugins, emptyList())

    server.removeContext("/plugins/api/search/updates/compatible")
    server.createContext("/plugins/api/search/updates/compatible") { handler ->
      handler.sendResponseHeaders(501, 0)
      handler.responseBody.writer().use {
        it.write("Internal Server Error")
      }
    }

    val result = UpdateCheckerFacade.getInstance().checkInstalledPluginUpdates()
    assertEquals(1, result.errors.size)

    val exception = result.errors.values.first()
    assertTrue(exception is HttpRequests.HttpStatusException)
    assertEquals(501, exception.statusCode)
  }

  @Test
  fun `alien plugin IDs are not send to Marketplace`() {
    installedPluginsFacade.setPlugins(listOf(
      InstalledPluginMock("ColourChooser", "Colour Chooser", "JetBrains", "1.0", "0", "999.99999", true),
      InstalledPluginMock("ImageView", "Image View", "JetBrains", "0.1", "1.0", "999.99999", true),
    ))

    val serverPlugins = listOf(
      RepositoryPluginMock("ColourChooser", "501", "101", "2.0"),
      // No ImageView plugin in repository
    )
    setServerPlugins(serverPlugins, serverPlugins)

    server.removeContext("/plugins/api/search/updates/compatible")
    server.createContext("/plugins/api/search/updates/compatible") { handler ->
      receivedUpdatesRequestUri = handler.requestURI

      handler.sendResponseHeaders(200, 0)
      handler.responseBody.writer().use {
        it.write(getUpdatesResponseJson(serverPlugins))
      }
    }

    UpdateCheckerFacade.getInstance().checkInstalledPluginUpdates()

    assertNotNull(receivedUpdatesRequestUri)

    val pluginIds = getPluginIdsFromQuery(receivedUpdatesRequestUri)
    assertEquals(1, pluginIds.size)
    assertEquals("ColourChooser", pluginIds.first())
  }

  @Test
  fun `single plugin update`() {
    installedPluginsFacade.setPlugins(listOf(
      InstalledPluginMock("ColourChooser", "Colour Chooser", "JetBrains", "1.0", "0", "999.99999", true),
      InstalledPluginMock("ImageView", "Image View", "JetBrains", "0.1", "1.0", "999.99999", true),
    ))

    val colourChooserPlugin = RepositoryPluginMock("ColourChooser", "501", "101", "2.0")
    val imageViewPlugin = RepositoryPluginMock("ImageView", "502", "102", "2.1")
    setServerPlugins(
      listOf(
        colourChooserPlugin,
        imageViewPlugin,
      ),
      listOf(imageViewPlugin)
    )

    val result = PluginUpdateCheckService.getInstance().getPluginUpdate(PluginId.getId("ImageView"))

    assertTrue(result is PluginUpdateInfo.UpdateAvailable)
    assertEquals("ImageView", result.update.id.idString)
  }

}