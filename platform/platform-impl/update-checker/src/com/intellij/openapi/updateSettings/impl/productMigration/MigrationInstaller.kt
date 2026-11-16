// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl.productMigration

import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.util.ExecUtil
import com.intellij.ide.IdeBundle
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.productMigration.readInstalledProductInfo
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.updateSettings.impl.DistributionInfo
import com.intellij.openapi.util.io.NioFiles
import com.intellij.util.SystemProperties
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.io.HttpRequests
import com.intellij.util.io.sha256Hex
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.util.Locale
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries

private val LOG = logger<MigrationInstallException>()

private const val DOWNLOAD_FILE_NAME = "distribution.dmg"
private const val PROCESS_TIMEOUT_MS = 30 * 60 * 1000
private const val DETACH_TIMEOUT_MS = 60 * 1000

private val SHA256_LINE = Regex("([0-9a-fA-F]{64})(?:\\s+\\*?\\S.*)?")

/**
 * A failure of the migration install. The notification shows [userMessage]. The log gets the exception message.
 */
@ApiStatus.Internal
class MigrationInstallException(val userMessage: @Nls String, logMessage: String, cause: Throwable? = null) : Exception(logMessage, cause)

/**
 * Returns the lower-case SHA-256 hex from the first non-blank line of a checksum file, or `null` for a bad format.
 * The accepted forms are `<hex>`, `<hex>  <name>` and `<hex> *<name>`. The name is not checked.
 */
@ApiStatus.Internal
fun parseSha256File(text: String): String? {
  val line = text.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() } ?: return null
  return SHA256_LINE.matchEntire(line)?.groupValues?.get(1)?.lowercase(Locale.ROOT)
}

/** Returns the one `*.app` directory in [mountPoint], or `null`. A symbolic link does not count. */
@ApiStatus.Internal
fun findSingleApp(mountPoint: Path): Path? =
  mountPoint.listDirectoryEntries("*.app").singleOrNull { it.isDirectory(LinkOption.NOFOLLOW_LINKS) }

/**
 * Runs [action] in an empty [workDir]. The folder is deleted after a success, a failure and a cancel.
 */
@ApiStatus.Internal
fun <T> withMigrationWorkDir(workDir: Path, action: (Path) -> T): T {
  NioFiles.deleteRecursively(workDir)
  try {
    Files.createDirectories(workDir)
    return action(workDir)
  }
  finally {
    deleteLogged(workDir)
  }
}

/** Downloads [url] into [workDir] and returns the file. Its SHA-256 must match the checksum file `<url>.sha256`, which loads first. */
@ApiStatus.Internal
@RequiresBackgroundThread
fun downloadAndVerify(
  url: String,
  workDir: Path,
  indicator: ProgressIndicator,
  allowFileUrls: Boolean = isFileContentUrlAllowed(),
): Path {
  if (!isAllowedContentUrl(url, allowFileUrls)) {
    throw MigrationInstallException(IdeBundle.message("updates.migration.content.invalid"), "the distribution URL is not allowed: ${url}")
  }

  indicator.text = IdeBundle.message("updates.migration.progress.checksum")
  val checksumUrl = "${url}.sha256"
  val checksumText = try {
    download(checksumUrl, indicator).toString(Charsets.UTF_8)
  }
  catch (e: Exception) {
    rethrowControlFlowException(e)
    throw checksumError("cannot load ${checksumUrl}: ${e.message}", e)
  }
  val expected = parseSha256File(checksumText) ?: throw checksumError("invalid checksum file ${checksumUrl}", cause = null)

  indicator.text = IdeBundle.message("updates.migration.progress.download")
  val target = workDir.resolve(DOWNLOAD_FILE_NAME)
  LOG.info("downloading ${url}")
  try {
    HttpRequests.request(url).productNameAsUserAgent().saveToFile(target, indicator, true)
  }
  catch (e: IOException) {
    val reason = e.message ?: e.toString()
    throw MigrationInstallException(IdeBundle.message("updates.migration.error.download", reason), "cannot download ${url}: ${reason}", e)
  }

  indicator.checkCanceled()
  indicator.text = IdeBundle.message("updates.migration.progress.verify")
  indicator.text2 = ""
  indicator.isIndeterminate = true
  val actual = sha256Hex(target)
  if (actual != expected) {
    throw checksumError("the checksum of ${url} does not match: expected ${expected}, actual ${actual}", cause = null)
  }
  return target
}

/** Downloads and verifies the file of [distribution], installs it into `~/Applications`, and returns the installed application. */
@RequiresBackgroundThread
internal fun installMigrationDistribution(distribution: DistributionInfo, indicator: ProgressIndicator): Path {
  return withMigrationWorkDir(PathManager.getTempDir().resolve("migration-update")) { workDir ->
    installMacApp(downloadAndVerify(distribution.fileUrl, workDir, indicator), distribution.product, indicator)
  }
}

private fun installMacApp(dmg: Path, product: String, indicator: ProgressIndicator): Path {
  indicator.text = IdeBundle.message("updates.migration.progress.install")
  indicator.text2 = ""
  indicator.isIndeterminate = true

  val mountPoint = Files.createTempDirectory(PathManager.getTempDir(), "migration-mount")
  try {
    runCommand(GeneralCommandLine("hdiutil", "attach", "-readonly", "-nobrowse", "-noautoopen", "-noautofsck", "-noverify",
                                  "-mountpoint", mountPoint.toString(), dmg.toString()), indicator)
    val app = findSingleApp(mountPoint) ?: throw installError("the disk image has no single application: ${mountPoint.listDirectoryEntries()}")

    val applications = Path.of(SystemProperties.getUserHome(), "Applications")
    Files.createDirectories(applications)
    val target = applications.resolve(app.fileName.toString())
    if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
      val productCode = readInstalledProductInfo(target)?.productCode
      if (productCode != product) {
        throw MigrationInstallException(IdeBundle.message("updates.migration.error.exists", target.toString()),
                                        "${target} has the product code ${productCode}, expected ${product}")
      }
      LOG.info("${target} is already installed and is not copied")
      return target
    }

    copyApp(app, target, indicator)
    return target
  }
  finally {
    detach(mountPoint)
  }
}

private fun copyApp(app: Path, target: Path, indicator: ProgressIndicator) {
  val partial = target.resolveSibling(".${target.fileName}.partial")
  NioFiles.deleteRecursively(partial)
  var copied = false
  try {
    runCommand(GeneralCommandLine("ditto", app.toString(), partial.toString()), indicator)
    Files.move(partial, target)
    copied = true
  }
  finally {
    if (!copied) {
      deleteLogged(partial)
    }
  }
}

private fun runCommand(command: GeneralCommandLine, indicator: ProgressIndicator) {
  val commandLine = command.commandLineString
  LOG.info("running ${commandLine}")
  val output = try {
    CapturingProcessHandler(command).runProcessWithProgressIndicator(indicator, PROCESS_TIMEOUT_MS)
  }
  catch (e: ExecutionException) {
    throw installError("cannot run ${commandLine}: ${e.message}", e)
  }
  if (output.isCancelled) {
    throw ProcessCanceledException()
  }
  if (output.isTimeout || output.exitCode != 0) {
    throw installError("${commandLine} failed: exit code ${output.exitCode}, timeout ${output.isTimeout}, ${output.stderr.trim()}")
  }
}

private fun detach(mountPoint: Path) {
  try {
    val output = ExecUtil.execAndGetOutput(GeneralCommandLine("hdiutil", "detach", "-force", mountPoint.toString()), DETACH_TIMEOUT_MS)
    if (output.exitCode != 0) {
      LOG.info("hdiutil detach ${mountPoint}: exit code ${output.exitCode}, ${output.stderr.trim()}")
    }
    Files.deleteIfExists(mountPoint)
  }
  catch (e: Exception) {
    rethrowControlFlowException(e)
    LOG.warn("cannot detach or delete ${mountPoint}", e)
  }
}

private fun deleteLogged(path: Path) {
  try {
    NioFiles.deleteRecursively(path)
  }
  catch (e: IOException) {
    LOG.warn("cannot delete ${path}", e)
  }
}

private fun checksumError(logMessage: String, cause: Throwable?): MigrationInstallException =
  MigrationInstallException(IdeBundle.message("updates.migration.error.checksum"), logMessage, cause)

private fun installError(reason: String, cause: Throwable? = null): MigrationInstallException =
  MigrationInstallException(IdeBundle.message("updates.migration.error.install"), reason, cause)
