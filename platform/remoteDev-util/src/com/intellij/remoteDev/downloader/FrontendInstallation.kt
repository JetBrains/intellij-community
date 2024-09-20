package com.intellij.remoteDev.downloader

import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.PathManager
import java.nio.file.Path
import kotlin.io.path.Path

/**
 * Describes an installation of the frontend on this machine.
 */
sealed interface FrontendInstallation {
  val installationHome: Path
  val buildNumber: String
}

/**
 * Describes a standalone frontend installation.
 */
class StandaloneFrontendInstallation(
  override val installationHome: Path,
  override val buildNumber: String,
  /** JBR is bundled with new versions of the frontend, this property is `null` in such cases */ 
  val jreDir: Path?,
) : FrontendInstallation

/**
 * Represents an installation of the frontend embedded in the currently running IDE.
 */
class EmbeddedFrontendInstallation(
  val frontendLauncher: EmbeddedClientLauncher
) : FrontendInstallation {
  override val buildNumber: String
    get() = ApplicationInfo.getInstance().build.asStringWithoutProductCode()
  override val installationHome: Path
    get() = Path(PathManager.getHomePath())
}
