package com.intellij.remoteDev.downloader

import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.util.BuildNumber
import java.nio.file.Path
import kotlin.io.path.Path

/**
 * Describes an installation of the frontend on this machine.
 */
sealed interface FrontendInstallation {
  val installationHome: Path
  val buildNumber: BuildNumber
}

/**
 * Describes a standalone frontend installation.
 */
class StandaloneFrontendInstallation(
  override val installationHome: Path,
  override val buildNumber: BuildNumber,
  /** JBR is bundled with new versions of the frontend, this property is `null` in such cases */ 
  val jreDir: Path?,
) : FrontendInstallation

/**
 * Represents an installation of the frontend embedded in the currently running IDE.
 */
class EmbeddedFrontendInstallation(
  val frontendLauncher: EmbeddedClientLauncher
) : FrontendInstallation {
  override val buildNumber: BuildNumber
    get() = ApplicationInfo.getInstance().build.withoutProductCode()
  override val installationHome: Path
    get() = Path(PathManager.getHomePath())
}
