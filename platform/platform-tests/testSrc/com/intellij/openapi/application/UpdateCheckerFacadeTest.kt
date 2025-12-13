// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.intellij.ide.plugins.*
import com.intellij.ide.plugins.marketplace.utils.MarketplaceCustomizationService
import com.intellij.internal.statistic.eventLog.fus.MachineIdManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.impl.ApplicationInfoImpl
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.observable.util.whenDisposed
import com.intellij.openapi.updateSettings.impl.UpdateCheckerFacade
import com.intellij.openapi.updateSettings.impl.UpdateCheckerPluginsFacade
import com.intellij.openapi.util.BuildNumber
import com.intellij.openapi.util.NlsSafe
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.disposableFixture
import com.intellij.testFramework.junit5.http.url
import com.intellij.testFramework.replaceService
import com.intellij.testFramework.utils.io.deleteChildrenRecursively
import com.intellij.util.application
import com.intellij.util.queryParameters
import com.intellij.util.system.CpuArch
import com.intellij.util.system.OS
import com.sun.net.httpserver.HttpServer
import org.apache.http.client.utils.URLEncodedUtils
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import java.net.InetSocketAddress
import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry
import kotlin.test.assertTrue

@TestApplication
class UpdateCheckerFacadeTest {
  private val testDisposable = disposableFixture()
  private val objectMapper = ObjectMapper()

  private fun createTestServer(disposable: Disposable): HttpServer {
    val server = HttpServer.create()!!
    server.bind(InetSocketAddress(0), 1)
    server.start()
    disposable.whenDisposed { server.stop(0) }
    return server
  }

  lateinit var server: HttpServer

  var receivedUpdatesRequestUri: URI? = null

  @BeforeEach
  fun setup() {
    server = createTestServer(testDisposable.get())

    application.replaceService(InstalledPluginsState::class.java, InstalledPluginsState(), testDisposable.get())
    application.replaceService(UpdateCheckerPluginsFacade::class.java, TestUpdateCheckerPluginsFacade(), testDisposable.get())
    application.replaceService(MarketplaceCustomizationService::class.java, TestMarketplaceCustomizationService(server.url),
                               testDisposable.get())

    server.createContext("/plugins/files/brokenPlugins.json") { handler ->
      handler.sendResponseHeaders(200, 0)
      handler.responseBody.writer().use {
        it.write("[]")
      }
    }
  }

  @AfterEach
  fun tearDown() {
    updateBrokenPlugins(emptyMap())

    val pluginTempPath = Path.of(PathManager.getPluginTempPath())
    pluginTempPath.deleteChildrenRecursively { true }
  }

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

    setServerPlugins(
      listOf(
        RepositoryPluginMock("ColourChooser", "501", "101", "0.1"),
        RepositoryPluginMock("ImageView", "502", "102", "0.5"),
      ),
      """
      [{"id": "101", "pluginId": "501", "pluginXmlId": "ColourChooser", "version": "0.1"},
      {"id": "102", "pluginId": "502", "pluginXmlId": "ImageView", "version": "0.5"}] 
      """.trimIndent()
    )

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

    setServerPlugins(
      listOf(
        RepositoryPluginMock("ColourChooser", "501", "101", "0.1"),
        RepositoryPluginMock("ImageView", "502", "102", "0.1"),
      ),
      """
      []
      """.trimIndent()
    )

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
    val parsedQuery = URLEncodedUtils.parse(receivedUpdatesRequestUri, StandardCharsets.UTF_8)
    val pluginIds = parsedQuery
      .filter { it.name.equals("pluginXmlId", true) }
      .map { it.value }

    assertEquals(2, pluginIds.size)
    assertTrue(pluginIds.contains("ImageView"))
    assertTrue(pluginIds.contains("ColourChooser"))
  }

  @Test
  fun `not installed yet plugin`() {
    installedPluginsFacade.setPlugins(listOf(
      InstalledPluginMock("ColourChooser", "Colour Chooser", "JetBrains", "1.0", "0", "999.99999", true),
      // no ImageView installed, but we ask for its versions
    ))

    setServerPlugins(
      listOf(
        RepositoryPluginMock("ColourChooser", "501", "101", "2.0"),
        RepositoryPluginMock("ImageView", "502", "102", "2.1"),
      ),
      """
      [{"id": "101", "pluginId": "501", "pluginXmlId": "ColourChooser", "version": "2.0"},
       {"id": "102", "pluginId": "502", "pluginXmlId": "ImageView", "version": "2.1"}] 
      """.trimIndent()
    )

    val result = UpdateCheckerFacade.getInstance().getInternalPluginUpdates(
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

    setServerPlugins(
      listOf(
        // incompatible with build number > 251.250
        RepositoryPluginMock(
          "ColourChooser", "501", "101", "2.0",
          getMetaJson("ColourChooser", "501", "2.0", "251.200", "251.250")
        ),
        RepositoryPluginMock(
          "ImageView", "502", "102", "2.1",
          getMetaJson("ImageView", "502", "2.1", "251.100", "251.450")
        ),
      ),
      """
        [{"id": "101", "pluginId": "501", "pluginXmlId": "ColourChooser", "version": "2.0"},
       {"id": "102", "pluginId": "502", "pluginXmlId": "ImageView", "version": "2.1"}] 
      """.trimIndent()
    )

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

    setServerPlugins(
      listOf(
        // incompatible with build number > 251.250
        RepositoryPluginMock(
          "ColourChooser", "501", "101", "1.0",
          getMetaJson("ColourChooser", "501", "2.0", "251.200", "251.250")
        ),
        RepositoryPluginMock(
          "ImageView", "502", "102", "2.1",
          getMetaJson("ImageView", "502", "2.1", "251.100", "251.400")
        ),
      ),
      """
        [{"id": "102", "pluginId": "502", "pluginXmlId": "ImageView", "version": "2.1"}] 
      """.trimIndent()
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

    setServerPlugins(
      listOf(
        RepositoryPluginMock("ColourChooser", "501", "101", "2.0"),
      ),
      """
        [{"id": "101", "pluginId": "501", "pluginXmlId": "ColourChooser", "version": "2.0"}] 
      """.trimIndent()
    )

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
  fun `disabled plugin`() {
    installedPluginsFacade.setPlugins(listOf(
      InstalledPluginMock("ColourChooser", "Colour Chooser", "JetBrains", "1.0", "0", "999.99999", true),
      InstalledPluginMock("ImageView", "Image View", "JetBrains", "0.1", "1.0", "999.99999", false),
    ))

    setServerPlugins(
      listOf(
        RepositoryPluginMock("ColourChooser", "501", "101", "2.0"),
        RepositoryPluginMock("ImageView", "502", "102", "2.1"),
      ),
      """
      [{"id": "101", "pluginId": "501", "pluginXmlId": "ColourChooser", "version": "2.0"},
       {"id": "102", "pluginId": "502", "pluginXmlId": "ImageView", "version": "2.1"}] 
      """.trimIndent()
    )

    val result = UpdateCheckerFacade.getInstance().checkInstalledPluginUpdates(
      buildNumber = BuildNumber.fromString("251.300")
    ).pluginUpdates

    assertEquals(1, result.allEnabled.size)
    assertEquals(1, result.allDisabled.size)
    assertEquals("ColourChooser", result.allEnabled.first().id.idString)
    assertEquals("ImageView", result.allDisabled.first().id.idString)
  }

  private fun setServerPlugins(
    plugins: List<RepositoryPluginMock>,
    @Language("JSON") updatesResponse: String,
  ) {
    server.createContext("/plugins/files/pluginsXMLIds.json") { handler ->
      handler.sendResponseHeaders(200, 0)
      handler.responseBody.writer().use { out ->
        out.write(objectMapper.writeValueAsString(plugins.map { it.pluginId }))
      }
    }

    for (mock in plugins) {
      server.createContext("/plugins/files/${mock.externalPluginId}/${mock.externalUpdateId}/meta.json") { handler ->
        handler.sendResponseHeaders(200, 0)
        handler.responseBody.writer().use {
          if (mock.customModelResponse != null) {
            it.write(mock.customModelResponse)
          }
          else {
            it.write(getMetaJson(mock.pluginId, mock.externalPluginId, mock.version, "1.0", "999.9999"))
          }
        }
      }
    }

    server.createContext("/plugins/api/search/updates/compatible") { handler ->
      receivedUpdatesRequestUri = handler.requestURI

      handler.sendResponseHeaders(200, 0)
      handler.responseBody.writer().use {
        it.write(updatesResponse)
      }
    }
  }

  private fun getMetaJson(pluginId: String, externalPluginId: String, version: String, sinceBuild: String, untilBuild: String): String {
    return """
              {
                "id": "${externalPluginId}",
                "xmlId": "${pluginId}",
                "name": "${pluginId} Plugin",
                "description": "${pluginId} Helps You!",
                "organization": "${pluginId} Company",
                "tags": ["Productivity"],
                "version": "${version}",
                "notes": "Fixed bugs",
                "dependencies": ["com.intellij.modules.lang"],
                "since": "${sinceBuild}",
                "until": "${untilBuild}",
                "size": 86085,
                "vendor": "YourCompany",
                "sourceCodeUrl": "https://example.com/plugin/${pluginId}"
              }
          """.trimIndent()
  }
}

private class TestMarketplaceCustomizationService(private val mockHost: String) : MarketplaceCustomizationService {
  override fun getPluginManagerUrl(): String = "$mockHost/plugins"
  override fun getPluginDownloadUrl(): String = "$mockHost/download"
  override fun getPluginsListUrl(): String = "$mockHost/list"
  override fun getPluginHomepageUrl(pluginId: PluginId): String = "$mockHost/plugin/$pluginId"
}

private data class RepositoryPluginMock(
  val pluginId: String,
  val externalPluginId: String,
  val externalUpdateId: String,
  val version: String,
  @param:Language("JSON") val customModelResponse: String? = null,
)

private data class InstalledPluginMock(
  val id: String,
  val name: String,
  val company: String,
  val version: String?,
  val sinceBuild: String?,
  val untilBuild: String?,
  val enabled: Boolean,
)

private val installedPluginsFacade: TestUpdateCheckerPluginsFacade
  get() = application.getService(UpdateCheckerPluginsFacade::class.java) as TestUpdateCheckerPluginsFacade

private class TestUpdateCheckerPluginsFacade : UpdateCheckerPluginsFacade {
  private val plugins = mutableListOf<InstalledPluginMock>()
  private val descriptors = mutableMapOf<PluginId, TestIdeaPluginDescriptor>()

  private val hosts = mutableListOf<String>()

  fun setPlugins(plugins: List<InstalledPluginMock>) {
    this.plugins.clear()
    this.plugins.addAll(plugins)

    this.descriptors.clear()
    this.descriptors.putAll(
      plugins.map { mock ->
        object : TestIdeaPluginDescriptor() {
          override fun getPluginId(): PluginId = PluginId.getId(mock.id)
          override fun getName(): @NlsSafe String = pluginId.idString
          override fun getSinceBuild(): @NlsSafe String? = mock.sinceBuild
          override fun getUntilBuild(): @NlsSafe String? = mock.untilBuild
          override fun getVersion(): @NlsSafe String? = mock.version
          override fun getDependencies(): List<IdeaPluginDependency> = listOf()

          @Suppress("OVERRIDE_DEPRECATION")
          override fun isEnabled(): Boolean = mock.enabled
          override fun isBundled(): Boolean = false
          override fun allowBundledUpdate(): Boolean = false
        }
      }
        .associateBy { it.pluginId }
    )
  }

  fun setHosts(repositories: List<String>) {
    this.hosts.clear()
    this.hosts.addAll(repositories)
  }

  override fun getPlugin(id: PluginId): IdeaPluginDescriptor? {
    return descriptors[id]
  }

  override fun getInstalledPlugins(): Collection<IdeaPluginDescriptor> {
    return descriptors.values
  }

  override fun getOnceInstalledIfExists(): Path? = null

  override fun isDisabled(id: PluginId): Boolean {
    return plugins.find { it.id == id.idString }
      ?.enabled == false
  }

  override fun isCompatible(
    descriptor: IdeaPluginDescriptor,
    buildNumber: BuildNumber?,
  ): Boolean {
    return PluginManagerCore.isCompatible(descriptor, buildNumber)
  }

  // See RepositoryHelper.getPluginHosts
  override fun getPluginHosts(): List<String?> {
    val data = mutableListOf<String?>()
    data.addAll(hosts)
    data.add(null)
    return data
  }
}