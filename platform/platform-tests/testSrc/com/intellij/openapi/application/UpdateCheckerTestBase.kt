// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.intellij.ide.plugins.IdeaPluginDependency
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.InstalledPluginsState
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.TestIdeaPluginDescriptor
import com.intellij.ide.plugins.marketplace.utils.MarketplaceCustomizationService
import com.intellij.ide.plugins.updateBrokenPlugins
import com.intellij.openapi.Disposable
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.observable.util.whenDisposed
import com.intellij.openapi.updateSettings.impl.UpdateCheckerPluginsFacade
import com.intellij.openapi.util.BuildNumber
import com.intellij.openapi.util.NlsSafe
import com.intellij.testFramework.junit5.fixture.disposableFixture
import com.intellij.testFramework.junit5.http.url
import com.intellij.testFramework.replaceService
import com.intellij.testFramework.utils.io.deleteChildrenRecursively
import com.intellij.util.application
import com.sun.net.httpserver.HttpServer
import org.apache.http.client.utils.URLEncodedUtils
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import java.net.InetSocketAddress
import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.Path

internal abstract class UpdateCheckerTestBase {
  protected val testDisposable = disposableFixture()
  protected val objectMapper = ObjectMapper()

  protected lateinit var server: HttpServer

  protected var receivedUpdatesRequestUri: URI? = null

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
  @Suppress("DEPRECATION")
  fun tearDown() {
    updateBrokenPlugins(emptyMap())

    val pluginTempPath = Path.of(PathManager.getPluginTempPath())
    pluginTempPath.deleteChildrenRecursively { true }
  }

  protected fun createTestServer(disposable: Disposable): HttpServer {
    val server = HttpServer.create()!!
    server.bind(InetSocketAddress(0), 1)
    server.start()
    disposable.whenDisposed { server.stop(0) }
    return server
  }

  protected fun setServerPlugins(
    plugins: List<RepositoryPluginMock>,
    updates: List<RepositoryPluginMock>,
  ) {
    server.createContext("/plugins/files/pluginsXMLIds.json") { handler ->
      handler.sendResponseHeaders(200, 0)
      handler.responseBody.writer().use { out ->
        out.write(objectMapper.writeValueAsString(plugins.map { it.pluginId }))
      }
    }

    for ((pluginId, externalPluginId, externalUpdateId, version, customModelResponse) in plugins) {
      server.createContext("/plugins/files/$externalPluginId/$externalUpdateId/meta.json") { handler ->
        handler.sendResponseHeaders(200, 0)
        handler.responseBody.writer().use {
          if (customModelResponse != null) {
            it.write(customModelResponse)
          }
          else {
            it.write(getMetaJson(pluginId, externalPluginId, version, "1.0", "999.9999"))
          }
        }
      }
    }

    server.createContext("/plugins/api/search/updates/compatible") { handler ->
      receivedUpdatesRequestUri = handler.requestURI

      handler.sendResponseHeaders(200, 0)
      handler.responseBody.writer().use {
        it.write(getUpdatesResponseJson(updates))
      }
    }
  }

  protected fun getUpdatesResponseJson(updates: List<RepositoryPluginMock>): String {
    return objectMapper.writeValueAsString(
      updates.map {
        linkedMapOf(
          "id" to it.externalUpdateId,
          "pluginId" to it.externalPluginId,
          "pluginXmlId" to it.pluginId,
          "version" to it.version,
        )
      }
    )
  }

  protected fun getMetaJson(pluginId: String, externalPluginId: String, version: String, sinceBuild: String, untilBuild: String): String {
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

  protected fun getPluginIdsFromQuery(uri: URI?): List<String?> {
    // Java URI does not support repeatable names for pluginXmlId
    val parsedQuery = URLEncodedUtils.parse(uri, StandardCharsets.UTF_8)
    val pluginIds = parsedQuery
      .filter { it.name.equals("pluginXmlId", true) }
      .map { it.value }
    return pluginIds
  }

  protected val installedPluginsFacade: TestUpdateCheckerPluginsFacade
    get() = application.getService(UpdateCheckerPluginsFacade::class.java) as TestUpdateCheckerPluginsFacade
}

internal class TestMarketplaceCustomizationService(private val mockHost: String, private val byJetBrains: Boolean = true) :
  MarketplaceCustomizationService {
  override fun usesJetBrainsPluginRepository(): Boolean = byJetBrains
  override fun getPluginManagerUrl(): String = "$mockHost/plugins"
  override fun getPluginDownloadUrl(): String = "$mockHost/download"
  override fun getPluginsListUrl(): String = "$mockHost/list"
  override fun getPluginHomepageUrl(pluginId: PluginId): String = "$mockHost/plugin/$pluginId"
}

internal data class RepositoryPluginMock(
  val pluginId: String,
  val externalPluginId: String,
  val externalUpdateId: String,
  val version: String,
  @param:Language("JSON") val customModelResponse: String? = null,
)

internal data class InstalledPluginMock(
  val id: String,
  val name: String,
  val company: String,
  val version: String?,
  val sinceBuild: String?,
  val untilBuild: String?,
  val enabled: Boolean,
)

internal class TestUpdateCheckerPluginsFacade : UpdateCheckerPluginsFacade {
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
