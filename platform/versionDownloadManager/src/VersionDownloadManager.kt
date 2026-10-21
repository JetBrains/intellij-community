// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.versionDownloadManager

import com.intellij.ide.IdeBundle
import com.intellij.ide.trustedProjects.TrustedProjects
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.NioFiles
import com.intellij.platform.eel.fs.EelFileUtils
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.util.text.SemVer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path

private const val EXTERNAL_DOWNLOADS_DIR_NAME: String = "external-downloads"

/**
 * Warning: This is an Experimental API that can change any time!
 *
 * Provides common logic for handling external resources in rem-dev friendly way.
 * The task of the VersionDownloadManager is to:
 * - handle concurrent requests of downloading external resources
 * - handle file management of the downloaded resources
 * - handle clean-up of unused resources (todo)
 * - handle security during installation
 * - handle access by clients to any available version
 *
 * External resources can be CLIs, runtimes or language services.
 *
 * All resources are saved to `external-downloads` in the IDE's system folder. A
 * particular resource has one root folder which name is the [id]. Under that one
 * the different versions of the resource are stored e.g.
 *
 * ```
 * external-downloads/
 *    - chrome-devtools-cli/
 *        - chrome-devtools-cli-1.12.3/...
 *        - chrome-devtools-cli-1.11.0/...
 *    - nodejs/...
 * ```
 *
 * Installed versions are marked as fully installed as long as a [ATOMIC_ENSURER_FILE_NAME]
 * file exists. If an installation has any files but not this flag, it should be considered
 * broken/not fully installed.
 *
 * @see GrandVersionDownloadManager
 */
@ApiStatus.Internal
abstract class VersionDownloadManager(
  val id: String,
) {
  protected open val resourceKeepDir: Path
    get() = PathManager.getSystemDir()
      .resolve(EXTERNAL_DOWNLOADS_DIR_NAME)
      .resolve(validatePathSegment(id))

  private val versionsAccessMutex = Mutex()

  init {
    validatePathSegment(id)
  }

  protected abstract suspend fun installInto(project: Project, version: SemVer, targetDir: Path)

  protected abstract suspend fun prepareTargetDir(targetDir: Path)

  protected open suspend fun assertSafeToInstall(project: Project, version: SemVer, targetDir: Path) {}

  /**
   * Get the latest AVAILABLE for downloading version
   * @param stable non-stable RC/EAP/similar version strings
   * which resolve with more than MAJOR.MINOR.PATCH will be skipped
   */
  abstract suspend fun getLatestVersion(stable: Boolean): SemVer

  /**
   * Get versions AVAILABLE for download.
   */
  abstract suspend fun getAvailableVersions(): List<SemVer>

  /**
   * @param stable non-stable RC/EAP/similar version strings
   * which resolve with more than MAJOR.MINOR.PATCH will be skipped
   * @throws VersionDownloadManagerError on issues with installation
   */
  suspend fun downloadLatest(project: Project, stable: Boolean): SemVer {
    val latest = getLatestVersion(stable)
    downloadItem(project, latest)
    return latest
  }

  /**
   * Deletes local version directories except for the latest installed stable version and the latest installed version.
   */
  suspend fun purgeNonLatest(project: Project) {
    withBackgroundProgress(project, IdeBundle.message("external.download.cleaning", id)) {
      versionsAccessMutex.withLock {
        withContext(Dispatchers.IO) {
          val root = resourceKeepDir

          if (!safeIsDirectory(root)) return@withContext

          val installedVersions = getInstalledVersions()
          val latest = installedVersions.firstOrNull()
          val latestStable = installedVersions.firstOrNull { it.preRelease == null }
          val versionsToKeep = setOfNotNull(latest, latestStable)

          Files.newDirectoryStream(root, "$id-*").use { dirs ->
            for (dir in dirs) {
              ensureActive()
              if (!safeIsDirectory(dir)) continue
              val version = SemVer.parseFromText(dir.fileName.toString().removePrefix("$id-")) ?: continue
              if (version !in versionsToKeep) deleteInstallation(dir)
            }
          }
        }
      }
    }
  }

  /**
   * [project] is used not for installing versions per project but to make use of [Project] available APIs
   * for the installation. The downloaded resources will be available at Application-level.
   *
   * @throws VersionDownloadManagerError on issues with installation
   */
  suspend fun downloadItem(project: Project, version: SemVer) {
    if (!TrustedProjects.isProjectTrusted(project)) throw VersionDownloadManagerError(IdeBundle.message("external.download.untrusted.project", id))
    val targetDir = versionPath(version)
    if (withContext(Dispatchers.IO) { isFullyInstalled(targetDir) }) return

    withBackgroundProgress(project, IdeBundle.message("external.download.installing", id)) {
      versionsAccessMutex.withLock {
        if (withContext(Dispatchers.IO) { isFullyInstalled(targetDir) }) return@withLock
        LOG.info("Installing $id...")
        try {
          withContext(Dispatchers.IO) {
            deleteInstallation(targetDir)
            NioFiles.createDirectories(targetDir)
          }
          prepareTargetDir(targetDir)
          assertSafeToInstall(project, version, targetDir)
          installInto(project, version, targetDir)
          withContext(Dispatchers.IO) { addFullyInstalledMarker(targetDir) }
          LOG.info("Installed $id")
        }
        catch (e: Throwable) {
          LOG.warn("Failed to install $id")
          withContext(NonCancellable + Dispatchers.IO) { deleteInstallation(targetDir) }
          throw e
        }
      }
    }
  }

  fun getVersion(version: SemVer): Path? = versionPath(version).takeIf { isFullyInstalled(it) }

  fun getInstalledVersions(): List<SemVer> {
    if (!safeIsDirectory(resourceKeepDir)) return emptyList()
    return Files.newDirectoryStream(resourceKeepDir, "$id-*").use { dirs ->
      dirs.filter { isFullyInstalled(it) }
        .mapNotNull { SemVer.parseFromText(it.fileName.toString().removePrefix("$id-")) }
        .sortedDescending()
    }
  }

  fun versionPath(version: SemVer): Path {
    val name = "$id-${validatePathSegment(version.rawVersion)}"
    val dir = resourceKeepDir.resolve(name).normalize()
    require(dir.parent == resourceKeepDir.normalize())
    return dir
  }

  private fun safeIsDirectory(path: Path): Boolean {
    return Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)
  }

  private fun isFullyInstalled(dir: Path): Boolean =
    safeIsDirectory(dir) &&
    Files.isRegularFile(dir.resolve(ATOMIC_ENSURER_FILE_NAME), LinkOption.NOFOLLOW_LINKS)

  private fun addFullyInstalledMarker(dir: Path) {
    Files.writeString(dir.resolve(ATOMIC_ENSURER_FILE_NAME), ATOMIC_ENSURER_CONTENT)
  }

  private fun deleteMarkerIfExists(dir: Path) {
    Files.deleteIfExists(dir.resolve(ATOMIC_ENSURER_FILE_NAME))
  }

  private fun deleteInstallation(dir: Path) {
    deleteMarkerIfExists(dir)
    EelFileUtils.deleteRecursively(dir)
  }

  companion object {
    const val ATOMIC_ENSURER_FILE_NAME: String = "AtomicEnsurer"
    private const val ATOMIC_ENSURER_CONTENT: String = "this file indicates that the tool installation by IDEA was complete"

    private val SAFE_PATH_SEGMENT: Regex = Regex("[A-Za-z0-9._-]+")
  }

  private fun validatePathSegment(value: String): String {
    require(SAFE_PATH_SEGMENT.matches(value))
    require(!value.contains("..") && !value.startsWith("."))
    return value
  }
}

private val LOG = logger<VersionDownloadManager>()
