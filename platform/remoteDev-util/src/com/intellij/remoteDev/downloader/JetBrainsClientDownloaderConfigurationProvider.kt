package com.intellij.remoteDev.downloader

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.SystemInfo
import com.intellij.remoteDev.RemoteDevSystemSettings
import com.intellij.remoteDev.util.onTerminationOrNow
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.reactive.Signal
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import org.jetbrains.annotations.ApiStatus
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.*

// If you want to provide a custom url:
// 1) set TestJetBrainsClientDownloaderConfigurationProvider as serviceImplementation in RemoteDevUtil.xml
// 2) call (service<JetBrainsClientDownloaderConfigurationProvider> as TestJetBrainsClientDownloaderConfigurationProvider)
// .startServerAndServeClient(lifetime, clientDistribution, clientJdkBuildTxt)
@ApiStatus.Experimental
interface JetBrainsClientDownloaderConfigurationProvider {

  companion object {
    const val THIN_CLIENT_DOWNLOAD_URL_KEY = "THIN_CLIENT_DOWNLOAD_URL"
    val thinClientDownloadUrlValue: String?
      get() = System.getenv(THIN_CLIENT_DOWNLOAD_URL_KEY)

    const val THIN_CLIENT_VERIFY_SIGNATURE_KEY = "THIN_CLIENT_VERIFY_SIGNATURE"
    val thinClientVerifySignatureValue: Boolean?
      get() = System.getenv(THIN_CLIENT_VERIFY_SIGNATURE_KEY)?.toBoolean()

    const val THIN_CLIENT_DOWNLOAD_LATEST_BUILD_FROM_CDN_FOR_SNAPSHOT_KEY = "THIN_CLIENT_DOWNLOAD_LATEST_BUILD_FROM_CDN_FOR_SNAPSHOT"
    val thinClientDownloadLatestBuildFromCDNForSnapshotValue: Boolean?
      get() = System.getenv(THIN_CLIENT_DOWNLOAD_LATEST_BUILD_FROM_CDN_FOR_SNAPSHOT_KEY)?.toBoolean()

    val customPropertiesAreSet
      get() = thinClientDownloadUrlValue != null && thinClientDownloadLatestBuildFromCDNForSnapshotValue != null && thinClientVerifySignatureValue != null
  }

  fun modifyClientCommandLine(clientCommandLine: GeneralCommandLine)

  val clientDownloadUrl: URI
  val jreDownloadUrl: URI
  val clientCachesDir: Path
  val clientVersionManagementEnabled: Boolean
  val modifiedDateInManifestIncluded: Boolean

  val verifySignature: Boolean

  /**
   * Due to macOS limitations, it's only possible to append to vmoptions, not patch it
   */
  fun patchVmOptions(vmOptionsFile: Path, connectionUri: URI)
  val clientLaunched: Signal<Unit>

  val downloadLatestBuildFromCDNForSnapshotHost: Boolean
}

@ApiStatus.Experimental
class RealJetBrainsClientDownloaderConfigurationProvider : JetBrainsClientDownloaderConfigurationProvider {
  override fun modifyClientCommandLine(clientCommandLine: GeneralCommandLine) { }

  override val clientDownloadUrl: URI
    get() {
      val envVar = JetBrainsClientDownloaderConfigurationProvider.thinClientDownloadUrlValue
      return envVar?.let { URI(it) } ?: RemoteDevSystemSettings.getClientDownloadUrl().value
    }
  override val jreDownloadUrl: URI
    get() = RemoteDevSystemSettings.getJreDownloadUrl().value

  override val clientCachesDir: Path get () {
    val downloadDestination = IntellijClientDownloaderSystemSettings.getDownloadDestination()
    if (downloadDestination.value != null) {
      return Path(downloadDestination.value)
    }

    return Path.of(PathManager.getDefaultSystemPathFor("JetBrainsClientDist"))
  }

  override val clientVersionManagementEnabled: Boolean
    get() = IntellijClientDownloaderSystemSettings.isVersionManagementEnabled()
  override val modifiedDateInManifestIncluded: Boolean
    get() = IntellijClientDownloaderSystemSettings.isModifiedDateInManifestIncluded()
  override val verifySignature: Boolean
    get() {
      val envVar = JetBrainsClientDownloaderConfigurationProvider.thinClientVerifySignatureValue
      return envVar ?: true
    }

  override fun patchVmOptions(vmOptionsFile: Path, connectionUri: URI) {

  }

  override val clientLaunched: Signal<Unit> = Signal()

  override val downloadLatestBuildFromCDNForSnapshotHost: Boolean
    get() {
      val envVar = JetBrainsClientDownloaderConfigurationProvider.thinClientDownloadLatestBuildFromCDNForSnapshotValue
      return envVar ?: true
    }
}

@ApiStatus.Experimental
class TestJetBrainsClientDownloaderConfigurationProvider : JetBrainsClientDownloaderConfigurationProvider {
  var x11DisplayForClient: String? = null
  var guestConfigFolder: Path? =  null
  var guestSystemFolder: Path? = null
  var guestLogFolder: Path? = null

  var isDebugEnabled = false
  var debugSuspendOnStart = false
  var debugPort = -1

  override fun modifyClientCommandLine(clientCommandLine: GeneralCommandLine) {
    x11DisplayForClient?.let {
      require(SystemInfo.isLinux) { "X11 display property makes sense only on Linux" }
      logger<TestJetBrainsClientDownloaderConfigurationProvider>().info("Setting env var DISPLAY for Guest process=$it")
      clientCommandLine.environment["DISPLAY"] = it
    }
  }

  override var clientDownloadUrl: URI = URI("https://download.jetbrains.com/idea/code-with-me/")
  override var jreDownloadUrl: URI = URI("https://download.jetbrains.com/idea/jbr/")
  override var clientCachesDir: Path = Files.createTempDirectory("jbc-test-storage")
  override var clientVersionManagementEnabled: Boolean = true
  override val modifiedDateInManifestIncluded: Boolean = true
  override var verifySignature: Boolean = true

  override val clientLaunched: Signal<Unit> = Signal()

  override val downloadLatestBuildFromCDNForSnapshotHost: Boolean
    get() {
      val envVar = JetBrainsClientDownloaderConfigurationProvider.thinClientDownloadLatestBuildFromCDNForSnapshotValue
      return envVar ?: false
    }

  override fun patchVmOptions(vmOptionsFile: Path, connectionUri: URI) {
    thisLogger().info("Patching $vmOptionsFile")

    val traceCategories = listOf("#com.jetbrains.rdserver.joinLinks", "#com.jetbrains.rd.platform.codeWithMe.network")

    val debugOptions = if (isDebugEnabled) {
      val suspendOnStart = if (debugSuspendOnStart) "y" else "n"

      // changed in Java 9, now we have to use *: to listen on all interfaces
      "-agentlib:jdwp=transport=dt_socket,server=y,suspend=$suspendOnStart,address=*:$debugPort"
    }
    else ""

    val testVmOptions = listOf(
      "-Djb.consents.confirmation.enabled=false", // hz
      "-Djb.privacy.policy.text=\"<!--999.999-->\"", // EULA
      "-Didea.initially.ask.config=never",
      "-Dfus.internal.test.mode=true",
      "-Didea.suppress.statistics.report=true",
      "-Drsch.send.usage.stat=false",
      "-Duse.linux.keychain=false",
      "-Dide.show.tips.on.startup.default.value=false",
      "-Didea.is.internal=true",
      "-DcodeWithMe.memory.only.certificate=true", // system keychain
      "-Dide.slow.operations.assertion=false",
      "-Deap.login.enabled=false",
      "-Didea.updates.url=http://127.0.0.1",
      "-Didea.config.path=${guestConfigFolder!!.absolutePathString()}",
      "-Didea.system.path=${guestSystemFolder!!.absolutePathString()}",
      "-Didea.log.path=${guestLogFolder!!.absolutePathString()}",
      "-Didea.log.trace.categories=${traceCategories.joinToString(",")}",
      debugOptions).joinToString(separator = "\n", prefix = "\n")

    require(vmOptionsFile.isRegularFile() && vmOptionsFile.exists())

    val originalContent = vmOptionsFile.readText(Charsets.UTF_8)
    thisLogger().info("Original .vmoptions=\n$originalContent")

    val patchedContent = originalContent + testVmOptions
    thisLogger().info("Patched .vmoptions=$patchedContent")

    vmOptionsFile.writeText(patchedContent)
    thisLogger().info("Patched $vmOptionsFile successfully")
  }

  private var tarGzServer: HttpServer? = null
  fun mockClientDownloadsServer(lifetime: Lifetime, ipv4Address: InetSocketAddress) : InetSocketAddress {
    require(tarGzServer == null)
    thisLogger().info("Initializing HTTP server to download distributions as if from outer world")

    val server = HttpServer.create(ipv4Address, 0)
    thisLogger().info("HTTP server is bound to ${server.address}")

    server.createContext("/")
    thisLogger().info("Starting http server at ${server.address}")


    clientDownloadUrl = URI("http:/${server.address}/")
    verifySignature = false

    lifetime.onTerminationOrNow {
      clientDownloadUrl = URI("INVALID")
      verifySignature = true

      tarGzServer = null

      server.stop(10)
    }

    server.start()

    tarGzServer = server

    return server.address
  }

  fun serveFile(file: Path) {
    require(file.exists())
    require(file.isRegularFile())

    val server = tarGzServer
    require(server != null)

    server.createContext("/${file.name}", HttpHandler { httpExchange ->
      httpExchange.sendResponseHeaders(200, file.fileSize())
      httpExchange.responseBody.use { responseBody ->
        file.inputStream().use {
          it.copyTo(responseBody, 1024 * 1024)
        }
      }
    })
  }


  @Suppress("unused")
  fun startServerAndServeClient(lifetime: Lifetime, clientDistribution: Path, clientJdkBuildTxt: Path) {
    require(clientJdkBuildTxt.name.endsWith(".txt")) { "Do not mix-up client archive and client jdk build txt arguments" }

    mockClientDownloadsServer(lifetime, InetSocketAddress(Inet4Address.getLoopbackAddress(), 0))
    serveFile(clientDistribution)
    serveFile(clientJdkBuildTxt)
  }
}
