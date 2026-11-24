// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl.productMigration

import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.process.ProcessOutput
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
private const val CODESIGN_DISPLAY_TIMEOUT_MS = 60 * 1000

private const val CODESIGN = "/usr/bin/codesign"
private const val CODESIGN_INVALID = 1
private const val CODESIGN_REQUIREMENT_FAILED = 3
internal const val ALLOW_UNSIGNED_PROPERTY: String = "ide.update.migration.allow.unsigned"
private const val TEAM_ID_PREFIX = "TeamIdentifier="
private val TEAM_ID = Regex("[A-Z0-9]{10}")

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
  isAllowedUrl: (String) -> Boolean = { isAllowedContentUrl(it) },
  isAllowedFinalUrl: (String) -> Boolean = { isAllowedFinalContentUrl(it) },
): Path {
  if (!isAllowedUrl(url)) {
    throw MigrationInstallException(IdeBundle.message("updates.migration.content.invalid"), "the distribution URL is not allowed: ${url}")
  }

  indicator.text = IdeBundle.message("updates.migration.progress.checksum")
  val checksumUrl = "${url}.sha256"
  val checksumText = try {
    download(checksumUrl, indicator, isAllowedUrl, isAllowedFinalUrl).toString(Charsets.UTF_8)
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
    connectToAllowedUrl(url, isAllowedUrl, isAllowedFinalUrl) { it.saveToFile(target, indicator, true) }
  }
  catch (e: MigrationUrlNotAllowedException) {
    throw MigrationInstallException(IdeBundle.message("updates.migration.content.invalid"), "cannot download ${url}: ${e.message}", e)
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
    return installMountedApp(app, applications, product, indicator) { isTrustedMacApp(it, indicator) }
  }
  finally {
    detach(mountPoint)
  }
}

/**
 * Copies [app] into [applications], or reuses an installed application with the product code [product].
 * The IDE uses an application only when [isTrusted] accepts it. It never deletes or changes an application that exists before the call.
 */
@ApiStatus.Internal
fun installMountedApp(app: Path, applications: Path, product: String, indicator: ProgressIndicator, isTrusted: (Path) -> Boolean): Path {
  Files.createDirectories(applications)
  val target = applications.resolve(app.fileName.toString())
  if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
    val productCode = readInstalledProductInfo(target)?.productCode
    if (productCode != product) {
      throw MigrationInstallException(IdeBundle.message("updates.migration.error.exists", target.toString()),
                                      "${target} has the product code ${productCode}, expected ${product}")
    }
    indicator.text = IdeBundle.message("updates.migration.progress.verify")
    if (!isTrusted(target)) {
      throw MigrationInstallException(IdeBundle.message("updates.migration.error.exists", target.toString()),
                                      "${target} fails the signature check")
    }
    LOG.info("${target} is already installed and is not copied")
    return target
  }

  copyApp(app, target, indicator, isTrusted)
  return target
}

private fun copyApp(app: Path, target: Path, indicator: ProgressIndicator, isTrusted: (Path) -> Boolean) {
  val partial = target.resolveSibling(".${target.fileName}.partial")
  NioFiles.deleteRecursively(partial)
  var copied = false
  try {
    runCommand(GeneralCommandLine("ditto", app.toString(), partial.toString()), indicator)
    indicator.text = IdeBundle.message("updates.migration.progress.verify")
    if (!isTrusted(partial)) {
      throw installError("${partial} fails the signature check")
    }
    Files.move(partial, target)
    copied = true
  }
  finally {
    if (!copied) {
      deleteLogged(partial)
    }
  }
}

/** Returns the value of the `TeamIdentifier=` line of the `codesign -dv` output, or `null` when the value is not a team ID. */
@ApiStatus.Internal
fun parseCodesignTeam(output: String): String? {
  val team = output.lineSequence().firstOrNull { it.startsWith(TEAM_ID_PREFIX) }?.removePrefix(TEAM_ID_PREFIX) ?: return null
  return team.takeIf { TEAM_ID.matches(it) }
}

/**
 * The two Developer ID fields reject an "Apple Development" certificate of the same team, which has the same OU.
 */
@ApiStatus.Internal
fun macCodeRequirement(team: String): String {
  require(TEAM_ID.matches(team)) { "not a team ID: ${team}" }
  return "anchor apple generic and certificate 1[field.1.2.840.113635.100.6.2.6] " +
         "and certificate leaf[field.1.2.840.113635.100.6.1.13] and certificate leaf[subject.OU] = \"${team}\""
}

/** Returns the team of the signature of [app], or `null` for no signature, an ad hoc signature, or a failure. */
private fun readMacAppTeam(app: Path): String? {
  val command = GeneralCommandLine(CODESIGN, "-dv", "--verbose=2", app.toString())
  val output = try {
    ExecUtil.execAndGetOutput(command, CODESIGN_DISPLAY_TIMEOUT_MS)
  }
  catch (e: ExecutionException) {
    LOG.warn("cannot run ${command.commandLineString}", e)
    return null
  }
  if (output.exitCode != 0) {
    LOG.info("${command.commandLineString}: exit code ${output.exitCode}, timeout ${output.isTimeout}, ${output.stderr.trim()}")
    return null
  }
  // codesign writes the information to stderr
  return parseCodesignTeam(output.stderr) ?: null.also { LOG.info("${app} has no team ID") }
}

/**
 * Returns `false` when the signature of [app] is not a valid signature of the team of the running IDE.
 * Throws the usual install error when the check cannot run.
 */
@ApiStatus.Internal
fun isTrustedMacApp(
  app: Path,
  indicator: ProgressIndicator,
  allowUnsigned: Boolean = System.getProperty(ALLOW_UNSIGNED_PROPERTY).toBoolean(),
  runningTeam: () -> String? = { runningAppBundle()?.let(::readMacAppTeam) },
): Boolean {
  if (allowUnsigned) {
    LOG.info("the signature check of ${app} is off: -D${ALLOW_UNSIGNED_PROPERTY}=true")
    return true
  }
  val team = runningTeam() ?: throw installError("the running IDE has no team ID, so the signature of ${app} cannot be checked")
  return macCodeSatisfies(app, macCodeRequirement(team), indicator)
}

/** Returns `false` when `codesign` rejects [app]. Throws the usual install error when `codesign` fails for another reason. */
private fun macCodeSatisfies(app: Path, requirement: String, indicator: ProgressIndicator): Boolean {
  val command = GeneralCommandLine(CODESIGN, "--verify", "--deep", "--strict", "-R=${requirement}", app.toString())
  val output = runProcess(command, indicator)
  return when (output.exitCode) {
    0 -> true
    CODESIGN_INVALID, CODESIGN_REQUIREMENT_FAILED -> {
      LOG.info("${app} fails the signature check: exit code ${output.exitCode}, ${output.stderr.trim()}")
      false
    }
    else -> throw commandError(command, output)
  }
}

private fun runningAppBundle(): Path? {
  val home = PathManager.getHomeDir()
  val app = home.parent
  if (home.fileName?.toString() != "Contents" || app?.fileName?.toString()?.endsWith(".app") != true) {
    LOG.info("the running IDE is not in an application bundle: ${home}")
    return null
  }
  return app
}

private fun runCommand(command: GeneralCommandLine, indicator: ProgressIndicator) {
  val output = runProcess(command, indicator)
  if (output.exitCode != 0) {
    throw commandError(command, output)
  }
}

/** It does not check the exit code. */
private fun runProcess(command: GeneralCommandLine, indicator: ProgressIndicator): ProcessOutput {
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
  if (output.isTimeout) {
    throw commandError(command, output)
  }
  return output
}

private fun commandError(command: GeneralCommandLine, output: ProcessOutput): MigrationInstallException =
  installError("${command.commandLineString} failed: exit code ${output.exitCode}, timeout ${output.isTimeout}, ${output.stderr.trim()}")

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

internal fun installError(reason: String, cause: Throwable? = null): MigrationInstallException =
  MigrationInstallException(IdeBundle.message("updates.migration.error.install"), reason, cause)
