package com.intellij.remoteDev.downloader

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.util.BuildNumber
import com.intellij.remoteDev.util.ClientVersionUtil
import com.intellij.remoteDev.util.ProductInfo
import java.nio.file.Path
import kotlin.io.path.Path

/**
 * Extension point for the Ide Services plugin.
 * Called every time before the JetBrains client runs.
 * Implementation can patch vmOptions file and install or update plugins by unpacking them into
 * plugins directory (like toolbox does).
 */
interface ConfigureClientHook {
  fun beforeRun(
    extractedJetBrainsClientData: FrontendInstallation,
    productInfo: ProductInfo,
    configPath: FrontendConfigPaths,
  )

  companion object {
    val EP: ExtensionPointName<ConfigureClientHook> = ExtensionPointName<ConfigureClientHook>("com.intellij.remoteDev.configureClientHook")
  }
}

data class FrontendConfigPaths(
  val pluginPath: Path,
  val vmOptionsPath: Path,
  val configPath: Path,
  val logPath: Path,
) {
  companion object {
    fun fromProductInfo(productInfo: ProductInfo): FrontendConfigPaths {
      val buildNumber = checkNotNull(BuildNumber.fromString(productInfo.buildNumber))
      val dataDirName = productInfo.dataDirectoryName
      val vmOptionsFile = productInfo.launch.firstNotNullOfOrNull { launchData ->
        val customCommand = launchData.customCommands?.firstOrNull { EmbeddedClientLauncher.isThinClientCustomCommand(it) }
        customCommand?.vmOptionsFilePath ?: launchData.vmOptionsFilePath
      }
      checkNotNull(vmOptionsFile) { "product_info doesn't contain vmOptionsFilePath" }
      val vmOptionsFileName = vmOptionsFile.substringAfterLast("/")
      val pluginPath = getDefaultClientPathFor(dataDirName, buildNumber, PathManager::getDefaultPluginPathFor, true)
      val configPath = getDefaultClientPathFor(dataDirName, buildNumber, PathManager::getDefaultConfigPathFor, false)
      val vmOptionsPath = configPath.resolve(vmOptionsFileName)
      val logPath = getDefaultClientPathFor(dataDirName, buildNumber, PathManager::getDefaultLogPathFor, true)
      return FrontendConfigPaths(
        pluginPath = pluginPath,
        vmOptionsPath = vmOptionsPath,
        configPath = configPath,
        logPath = logPath
      )
    }

    fun getDefaultClientPathFor(dataDirName: String, buildNumber: BuildNumber, getDefaultPathFor: (String) -> String, useFrontendSuffixSince242: Boolean): Path {
      if (ClientVersionUtil.isClientUsesTheSamePathsAsLocalIde(buildNumber.asStringWithoutProductCode())) {
        if (!dataDirName.startsWith("JetBrainsClient")) {
          var result = Path(getDefaultPathFor(dataDirName))
          if (useFrontendSuffixSince242) {
            result = result.resolve("frontend")
          }
          return result
        }
      }
      return Path(getDefaultPathFor(dataDirName))
    }

  }
}
