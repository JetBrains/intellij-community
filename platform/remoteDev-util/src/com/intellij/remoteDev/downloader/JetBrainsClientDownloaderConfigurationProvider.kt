package com.intellij.remoteDev.downloader

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.SystemInfo
import com.intellij.remoteDev.util.getJetBrainsSystemCachesDir
import com.intellij.util.io.exists
import com.intellij.util.io.isFile
import org.jetbrains.annotations.ApiStatus
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.div
import kotlin.io.path.readText
import kotlin.io.path.writeText

@ApiStatus.Experimental
interface JetBrainsClientDownloaderConfigurationProvider {
  fun modifyClientCommandLine(clientCommandLine: GeneralCommandLine)

  val clientDownloadLocation: URI
  val jreDownloadLocation: URI
  val clientCachesDir: Path

  val verifySignature: Boolean

  fun patchVmOptions(vmOptionsFile: Path)
}

@ApiStatus.Experimental
class RealJetBrainsClientDownloaderConfigurationProvider : JetBrainsClientDownloaderConfigurationProvider {
  override fun modifyClientCommandLine(clientCommandLine: GeneralCommandLine) { }

  override val clientDownloadLocation: URI = URI("https://cache-redirector.jetbrains.com/download.jetbrains.com/idea/code-with-me/")
  override val jreDownloadLocation: URI = URI("https://cache-redirector.jetbrains.com/download.jetbrains.com/idea/jbr/")
  override val clientCachesDir: Path = getJetBrainsSystemCachesDir() / "JetBrainsClientDist"
  override val verifySignature: Boolean = true

  override fun patchVmOptions(vmOptionsFile: Path) { }
}

@ApiStatus.Experimental
class TestJetBrainsClientDownloaderConfigurationProvider : JetBrainsClientDownloaderConfigurationProvider {

  var x11DisplayForClient: String? = null
  var guestConfigFolder: Path? =  null
  var guestSystemFolder: Path? = null
  var guestLogFolder: Path? = null

  override fun modifyClientCommandLine(clientCommandLine: GeneralCommandLine) {
    x11DisplayForClient?.let {
      require(SystemInfo.isLinux) { "X11 display property makes sense only on Linux" }
      logger<TestJetBrainsClientDownloaderConfigurationProvider>().info("Setting env var DISPLAY for Guest process=$it")
      clientCommandLine.environment["DISPLAY"] = it
    }
  }

  override var clientDownloadLocation: URI = URI("https://cache-redirector.jetbrains.com/download.jetbrains.com/idea/code-with-me/")
  override var jreDownloadLocation: URI = URI("https://cache-redirector.jetbrains.com/download.jetbrains.com/idea/jbr/")
  override var clientCachesDir: Path = Files.createTempDirectory("")
  override var verifySignature: Boolean = true

  override fun patchVmOptions(vmOptionsFile: Path) {
    thisLogger().info("Patching $vmOptionsFile")

    val traceCategories = listOf("#com.jetbrains.rdserver.joinLinks", "#com.jetbrains.rd.platform.codeWithMe.network")
    val testVmOptions = listOf(
      "-Djb.consents.confirmation.enabled=false", // hz
      "-Djb.privacy.policy.text=\"<!--999.999-->\"", // EULA
      "-Didea.initially.ask.config=force-not",
      "-Dfus.internal.test.mode=true",
      "-Didea.suppress.statistics.report=true",
      "-Drsch.send.usage.stat=false",
      "-Duse.linux.keychain=false",
      "-Dide.show.tips.on.startup.default.value=false",
      "-Didea.is.internal=true",
      "-DcodeWithMe.memory.only.certificate=true", // system keychain
      "-Dide.slow.operations.assertion=false",
      "-Deap.login.enabled=false",
      "-Didea.config.path=${guestConfigFolder!!.absolutePathString()}",
      "-Didea.system.path=${guestSystemFolder!!.absolutePathString()}",
      "-Didea.log.path=${guestLogFolder!!.absolutePathString()}",
      "-Didea.log.trace.categories=${traceCategories.joinToString(",")}").joinToString(separator = "\n", prefix = "\n")

    require(vmOptionsFile.isFile() && vmOptionsFile.exists())

    val originalContent = vmOptionsFile.readText(Charsets.UTF_8)
    thisLogger().info("Original .vmoptions=\n$originalContent")

    val patchedContent = originalContent + testVmOptions
    thisLogger().info("Patched .vmoptions=$patchedContent")

    vmOptionsFile.writeText(patchedContent)
    thisLogger().info("Patched $vmOptionsFile successfully")
  }
}