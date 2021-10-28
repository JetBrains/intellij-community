package com.intellij.remoteDev.downloader

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.SystemInfo
import com.intellij.remoteDev.util.getJetBrainsSystemCachesDir
import org.jetbrains.annotations.ApiStatus
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.div

@ApiStatus.Experimental
interface JetBrainsClientDownloaderConfigurationProvider {
  fun modifyClientCommandLine(clientCommandLine: GeneralCommandLine)

  val clientDownloadLocation: URI
  val jreDownloadLocation: URI
  val clientCachesDir: Path

  val verifySignature: Boolean
}

@ApiStatus.Experimental
class RealJetBrainsClientDownloaderConfigurationProvider : JetBrainsClientDownloaderConfigurationProvider {
  override fun modifyClientCommandLine(clientCommandLine: GeneralCommandLine) { }

  override val clientDownloadLocation: URI = URI("https://cache-redirector.jetbrains.com/download.jetbrains.com/idea/code-with-me/")
  override val jreDownloadLocation: URI = URI("https://cache-redirector.jetbrains.com/download.jetbrains.com/idea/jbr/")
  override val clientCachesDir: Path = getJetBrainsSystemCachesDir() / "JetBrainsClientDist"
  override val verifySignature: Boolean = true
}

@ApiStatus.Experimental
class TestJetBrainsClientDownloaderConfigurationProvider : JetBrainsClientDownloaderConfigurationProvider {

  var X11DisplayForClient: String? = null

  override fun modifyClientCommandLine(clientCommandLine: GeneralCommandLine) {
    X11DisplayForClient?.let {
      require(SystemInfo.isLinux) { "X11 display property makes sense only on Linux" }
      logger<TestJetBrainsClientDownloaderConfigurationProvider>().info("Setting env var DISPLAY for Guest process=$it")
      clientCommandLine.environment["DISPLAY"] = it
    }
  }

  override var clientDownloadLocation: URI = URI("https://cache-redirector.jetbrains.com/download.jetbrains.com/idea/code-with-me/")
  override var jreDownloadLocation: URI = URI("https://cache-redirector.jetbrains.com/download.jetbrains.com/idea/jbr/")
  override var clientCachesDir: Path = Files.createTempDirectory("")
  override var verifySignature: Boolean = true
}