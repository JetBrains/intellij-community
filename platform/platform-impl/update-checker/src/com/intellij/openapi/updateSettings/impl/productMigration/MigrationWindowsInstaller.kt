// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl.productMigration

import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.ide.IdeBundle
import com.intellij.openapi.application.productMigration.readInstalledProductInfo
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.updateSettings.impl.DistributionInfo
import com.intellij.util.TimeoutUtil
import com.intellij.util.system.OS
import com.intellij.util.system.WindowsShell
import org.jetbrains.annotations.ApiStatus
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path

private val LOG = logger<MigrationInstallException>()

/**
 * The name of the install folder in the folder for the programs of the user.
 * The name is acceptable now. It is possibly not the final one, because the final name of the Unified IDE is not known.
 * It does not change with the version, so a later move of another IDE of the same user finds the Unified IDE and uses it.
 */
private const val WINDOWS_INSTALL_DIR_NAME = "JetBrains"

private const val SILENT_CONFIG_FILE_NAME = "silent.config"
private const val INSTALL_LOG_FILE_NAME = "install.log"
private const val UNINSTALLER_PATH = "bin/Uninstall.exe"
private const val INSTALLER_TIMEOUT_MS = 10 * 60 * 1000
private const val UNINSTALLER_TIMEOUT_MS = 60 * 1000
private const val UNINSTALL_WAIT_MS = 60 * 1000L
private const val UNINSTALL_POLL_MS = 250L
private const val INSTALL_LOG_TAIL_LENGTH = 4000

/**
 * The config of a silent install: an install for the current user.
 * It has no other option, so the install changes no `PATH`, no context menu and no file association, and it makes no desktop shortcut.
 * Without a config, the installer asks for administrator rights.
 */
@ApiStatus.Internal
const val WINDOWS_SILENT_CONFIG: String = "mode=user\r\n"

/**
 * The arguments of a silent install into [target], or `null` when the installer cannot read the paths.
 *
 * The installer accepts no quotes around an option, and Java puts quotes around an argument that is empty or has a space.
 * So each option is split at its spaces: the installer then gets the text of the option with single spaces and no quotes.
 * A path with two spaces in a row gives an empty argument, so it has no result. A tab and a quote have no result either.
 * `/D=` is the last option, because the installer reads its value to the end of the command line.
 *
 * `/NCRC` turns off the self-check of the installer, which shows a window while it reads a large file.
 * The IDE verifies the checksum and the signature of the file before the start.
 */
@ApiStatus.Internal
fun windowsInstallerArguments(config: Path, log: Path, target: Path): List<String>? {
  val arguments = listOf("/S", "/NCRC") + listOf("/CONFIG=${config}", "/LOG=${log}", "/D=${target}").flatMap { it.split(' ') }
  return arguments.takeIf { all -> all.none { it.isEmpty() || '\t' in it || '"' in it } }
}

/**
 * Returns the Unified IDE with the product code [product] in [target], or `null` when the installer can use [target].
 * The installer can use a folder that does not exist or is empty.
 * The IDE uses an installed application only when [isTrusted] accepts its launcher. Other content gives an error, and it does not change.
 */
@ApiStatus.Internal
fun findInstalledWindowsApp(target: Path, product: String, isTrusted: (Path) -> Boolean): Path? {
  if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS) || isEmptyDirectory(target)) {
    return null
  }
  val info = readInstalledProductInfo(target, OS.Windows)
  if (info == null || info.productCode != product) {
    throw existsError(target, "${target} has the product code ${info?.productCode}, expected ${product}")
  }
  val launcher = info.launcher ?: throw existsError(target, "${target} has no launcher")
  if (!isTrusted(launcher)) {
    throw existsError(target, "${launcher} fails the signature check")
  }
  LOG.info("${target} is already installed, and the installer does not run")
  return target
}

/**
 * Installs the distribution with the product code [product] into [target], or uses the Unified IDE that is already there.
 *
 * [download] gives the installer file. The installer runs only when [isTrusted] accepts it.
 * [runInstaller] starts the installer with the arguments of [windowsInstallerArguments] and returns its exit code.
 * The exit code 0 is not proof of an install, so [target] must have the product code [product] after it.
 * After a failure and after a cancel of the installer, [removeFailedInstall] removes what the installer made in [target].
 */
@ApiStatus.Internal
fun installWindowsApp(
  target: Path,
  product: String,
  workDir: Path,
  indicator: ProgressIndicator,
  isTrusted: (Path) -> Boolean,
  download: () -> Path,
  runInstaller: (installer: Path, arguments: List<String>) -> Int,
  removeFailedInstall: (Path) -> Unit,
): Path {
  val config = workDir.resolve(SILENT_CONFIG_FILE_NAME)
  val arguments = windowsInstallerArguments(config, workDir.resolve(INSTALL_LOG_FILE_NAME), target)
                  ?: throw installError("the installer cannot read a path with two spaces in a row, a tab or a quote: ${workDir}, ${target}")
  indicator.text = IdeBundle.message("updates.migration.progress.verify")
  findInstalledWindowsApp(target, product, isTrusted)?.let { return it }

  val installer = download()
  indicator.text = IdeBundle.message("updates.migration.progress.verify")
  if (!isTrusted(installer)) {
    throw installError("${installer} fails the signature check")
  }

  indicator.text = IdeBundle.message("updates.migration.progress.install")
  indicator.text2 = ""
  indicator.isIndeterminate = true
  // another IDE of the user can install the Unified IDE during the download
  findInstalledWindowsApp(target, product, isTrusted)?.let { return it }
  try {
    Files.writeString(config, WINDOWS_SILENT_CONFIG, Charsets.US_ASCII)
  }
  catch (e: IOException) {
    throw installError("cannot write ${config}: ${e.message}", e)
  }

  var installed = false
  try {
    val exitCode = runInstaller(installer, arguments)
    if (exitCode != 0) {
      throw installError("the installer failed: exit code ${exitCode}")
    }
    val productCode = readInstalledProductInfo(target, OS.Windows)?.productCode
    if (productCode != product) {
      throw installError("${target} has the product code ${productCode} after the install, expected ${product}")
    }
    installed = true
    return target
  }
  finally {
    if (!installed) {
      removeFailedInstall(target)
    }
  }
}

/** Installs [distribution] for the current user with its installer, with no window and no prompt for administrator rights. */
internal fun installWindowsDistribution(distribution: DistributionInfo, workDir: Path, indicator: ProgressIndicator): Path {
  val programs = WindowsShell.userProgramFilesDir() ?: throw installError("the folder for the programs of the user is unknown")
  return installWindowsApp(
    target = programs.resolve(WINDOWS_INSTALL_DIR_NAME),
    product = distribution.product,
    workDir = workDir,
    indicator = indicator,
    isTrusted = { isTrustedWindowsFile(it) },
    download = { downloadAndVerify(distribution.fileUrl, workDir, indicator) },
    runInstaller = { installer, arguments -> runWindowsInstaller(installer, arguments, workDir, indicator) },
    removeFailedInstall = ::removeFailedWindowsInstall,
  )
}

/**
 * Runs [installer] with [arguments] and returns its exit code. A cancel of [indicator] and the time limit end its process tree.
 * A blocked installer shows a message box and does not exit, so the time limit is necessary.
 */
private fun runWindowsInstaller(installer: Path, arguments: List<String>, workDir: Path, indicator: ProgressIndicator): Int {
  val command = listOf(installer.toString()) + arguments
  val commandLine = command.joinToString(" ")
  LOG.info("running ${commandLine}")
  try {
    val handler = CapturingProcessHandler(startWithoutOutput(command, workDir), Charsets.UTF_8, commandLine)
    val exitCode = runProcess(handler, commandLine, indicator, INSTALLER_TIMEOUT_MS).exitCode
    if (exitCode != 0) {
      logInstallerLog(workDir)
    }
    return exitCode
  }
  catch (e: IOException) {
    throw installError("cannot run ${commandLine}: ${e.message}", e)
  }
  catch (e: MigrationInstallException) {
    logInstallerLog(workDir)
    throw e
  }
}

/**
 * Removes what a failed or a cancelled install made in [target]: the folder, the uninstall record and the Start menu shortcut.
 * The uninstaller of a complete install removes the record and the shortcut. An install that did not end has none of them.
 */
@ApiStatus.Internal
fun removeFailedWindowsInstall(target: Path) {
  val uninstaller = target.resolve(UNINSTALLER_PATH)
  // without the launcher next to it, the uninstaller shows a message box
  if (Files.isRegularFile(uninstaller) && readInstalledProductInfo(target, OS.Windows)?.launcher != null) {
    runWindowsUninstaller(uninstaller, target)
  }
  deleteLogged(target)
}

private fun runWindowsUninstaller(uninstaller: Path, target: Path) {
  val command = listOf(uninstaller.toString(), "/S")
  val commandLine = command.joinToString(" ")
  LOG.info("running ${commandLine}")
  try {
    val output = CapturingProcessHandler(startWithoutOutput(command, uninstaller.parent), Charsets.UTF_8, commandLine)
      .runProcess(UNINSTALLER_TIMEOUT_MS)
    if (output.isTimeout || output.exitCode != 0) {
      // no copy of the uninstaller works then, so the folder does not go
      LOG.info("${commandLine}: exit code ${output.exitCode}, timeout ${output.isTimeout}")
      return
    }
  }
  catch (e: IOException) {
    LOG.warn("cannot run ${commandLine}", e)
    return
  }

  // the uninstaller starts a copy of itself and exits before the copy removes the files
  val deadline = System.currentTimeMillis() + UNINSTALL_WAIT_MS
  while (Files.exists(target, LinkOption.NOFOLLOW_LINKS) && System.currentTimeMillis() < deadline) {
    TimeoutUtil.sleep(UNINSTALL_POLL_MS)
  }
  if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
    LOG.info("the uninstaller does not remove ${target} in ${UNINSTALL_WAIT_MS} ms")
  }
}

/** `ProcessBuilder` and not `GeneralCommandLine`, which also puts quotes around an argument with a parenthesis or an apostrophe. */
@Throws(IOException::class)
private fun startWithoutOutput(command: List<String>, workDir: Path): Process =
  ProcessBuilder(command)
    .directory(workDir.toFile())
    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
    .redirectError(ProcessBuilder.Redirect.DISCARD)
    .start()

private fun logInstallerLog(workDir: Path) {
  val log = workDir.resolve(INSTALL_LOG_FILE_NAME)
  try {
    if (Files.isRegularFile(log)) {
      LOG.info("the end of the installer log:\n${Files.readString(log, Charsets.ISO_8859_1).takeLast(INSTALL_LOG_TAIL_LENGTH)}")
    }
  }
  catch (e: IOException) {
    LOG.info("cannot read ${log}: ${e.message}")
  }
}

private fun isEmptyDirectory(path: Path): Boolean {
  if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
    return false
  }
  return try {
    Files.newDirectoryStream(path).use { !it.iterator().hasNext() }
  }
  catch (e: IOException) {
    LOG.info("cannot list ${path}: ${e.message}")
    false
  }
}

private fun existsError(target: Path, logMessage: String): MigrationInstallException =
  MigrationInstallException(IdeBundle.message("updates.migration.error.exists", target.toString()), logMessage)
