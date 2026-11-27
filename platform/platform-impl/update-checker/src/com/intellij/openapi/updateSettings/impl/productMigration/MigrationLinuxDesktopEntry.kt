// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl.productMigration

import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.openapi.application.productMigration.readInstalledProductInfo
import com.intellij.openapi.diagnostic.logger
import com.intellij.util.system.OS
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermissions
import java.util.Locale

private val LOG = logger<MigrationInstallException>()

private const val TRY_EXEC_KEY = "TryExec="
private const val DESKTOP_ENTRY_GROUP = "[Desktop Entry]"
private val FILE_NAME_PART = Regex("[A-Za-z0-9._-]+")

/**
 * The text of the `.desktop` file of an installed IDE, by the Desktop Entry Specification, or `null`.
 * `TryExec` names [launcher], so the desktop hides the entry when the user deletes the IDE.
 * With no [icon], the text has no `Icon` line. With no [wmClass], it has no `StartupWMClass` line.
 * The result is `null` when a value has a control character, because the format has no safe form for it.
 */
@ApiStatus.Internal
fun linuxDesktopEntry(name: String, launcher: Path, icon: Path?, wmClass: String?): String? {
  val values = listOfNotNull(name, launcher.toString(), icon?.toString(), wmClass)
  if (values.any { value -> value.any { it.isISOControl() } }) return null
  return buildString {
    append(DESKTOP_ENTRY_GROUP).append('\n')
    append("Version=1.0\n")
    append("Type=Application\n")
    append("Name=").append(escapeString(name)).append('\n')
    if (icon != null) append("Icon=").append(escapeString(icon.toString())).append('\n')
    append("Exec=").append(escapeExecArgument(launcher.toString())).append(" %f\n")
    append(TRY_EXEC_KEY).append(escapeString(launcher.toString())).append('\n')
    append("Categories=Development;IDE;\n")
    append("Terminal=false\n")
    if (wmClass != null) append("StartupWMClass=").append(escapeString(wmClass)).append('\n')
    append("StartupNotify=true\n")
  }
}

/** The escape rule of a value of the type `string`. */
private fun escapeString(value: String): String = value.replace("\\", "\\\\")

/** The text of a value of the type `string`, or `null` when the value has an escape that the type does not have. */
private fun unescapeString(value: String): String? {
  val result = StringBuilder()
  var i = 0
  while (i < value.length) {
    val c = value[i++]
    if (c != '\\') {
      result.append(c)
      continue
    }
    when (value.getOrNull(i++)) {
      '\\' -> result.append('\\')
      's' -> result.append(' ')
      'n' -> result.append('\n')
      't' -> result.append('\t')
      'r' -> result.append('\r')
      else -> return null
    }
  }
  return result.toString()
}

/**
 * An argument of the `Exec` key: in double quotes, with a backslash before each character that the quotes do not protect.
 * Then the rule of a `string` value applies, and `%` becomes `%%`, because `%` starts a field code.
 */
private fun escapeExecArgument(value: String): String {
  val quoted = buildString {
    append('"')
    for (c in value) {
      if (c == '"' || c == '`' || c == '$' || c == '\\') append('\\')
      append(c)
    }
    append('"')
  }
  return escapeString(quoted).replace("%", "%%")
}

/**
 * The name of the `.desktop` file: `<wmClass>.desktop`, the name that "Create Desktop Entry" of an IDE uses.
 * With no [wmClass], the name has the product code. The result is `null` when the name is not a safe file name.
 */
@ApiStatus.Internal
fun linuxDesktopEntryFileName(wmClass: String?, product: String): String? {
  val name = wmClass ?: "jetbrains-${product.lowercase(Locale.ROOT)}"
  return if (FILE_NAME_PART.matches(name) && !name.startsWith('.')) "${name}.desktop" else null
}

/**
 * Returns `false` only when the `TryExec` key of the `.desktop` [file] has an absolute path that does not exist.
 * Each other file must not change: it is an entry of the user or of a live install.
 * Only the `[Desktop Entry]` group has the key. The desktop looks for a `TryExec` value with no folder in `PATH`.
 */
private fun isLiveDesktopEntry(file: Path): Boolean {
  val line = Files.readAllLines(file)
               .dropWhile { it.trim() != DESKTOP_ENTRY_GROUP }
               .drop(1)
               .takeWhile { !it.startsWith('[') }
               .firstOrNull { it.startsWith(TRY_EXEC_KEY) } ?: return true
  val value = unescapeString(line.removePrefix(TRY_EXEC_KEY).trim()) ?: return true
  return try {
    val path = Path.of(value)
    !path.isAbsolute || Files.exists(path)
  }
  catch (_: InvalidPathException) {
    true
  }
}

/**
 * Writes the entry of the application menu for the installed IDE [app] into [applications], for the current user.
 * The values come from the `product-info.json` of [app]. See [linuxDesktopEntry] and [linuxDesktopEntryFileName].
 * A file that exists changes only when its `TryExec` is an absolute path that does not exist.
 * A failure is a warning in the log, because the install is good without the entry.
 */
@ApiStatus.Internal
fun writeLinuxDesktopEntry(app: Path, product: String, applications: Path) {
  try {
    val info = readInstalledProductInfo(app, OS.Linux)
    val launcher = info?.launcher
    val name = info?.name?.takeIf { it.isNotBlank() }
    if (launcher == null || name == null) {
      LOG.warn("no menu entry: ${app} has no launcher or no name")
      return
    }
    val wmClass = info.startupWmClass?.takeIf { it.isNotBlank() }
    val fileName = linuxDesktopEntryFileName(wmClass, product)
    val text = linuxDesktopEntry(name, launcher, info.icon, wmClass)
    if (fileName == null || text == null) {
      LOG.warn("no menu entry: a value of ${app} has a character that the entry cannot have")
      return
    }

    val file = applications.resolve(fileName)
    if (Files.exists(file, LinkOption.NOFOLLOW_LINKS) && isLiveDesktopEntry(file)) {
      LOG.info("the menu entry ${file} exists and does not change")
      return
    }
    Files.createDirectories(applications)
    // the desktop reads each `*.desktop` file of the folder, so the temporary file has another name
    val temp = Files.createTempFile(applications, ".migration-", ".tmp", PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-r--r--")))
    try {
      Files.writeString(temp, text)
      Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }
    finally {
      Files.deleteIfExists(temp)
    }
    LOG.info("the menu entry is ${file}")
  }
  catch (e: Exception) {
    rethrowControlFlowException(e)
    LOG.warn("cannot write the menu entry of ${app}", e)
  }
}
