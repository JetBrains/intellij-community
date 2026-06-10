// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.pi.sessions

// @spec community/plugins/agent-workbench/spec/agent-sessions-pi.spec.md

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.platform.eel.fs.EelFiles
import com.intellij.util.io.DigestUtil
import com.intellij.util.io.sha256Hex
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

private val THEME_LOG = logger<PiThemeSupport>()

internal class PiThemeSupport(
  private val rootDirectoryProvider: () -> Path = ::defaultPiThemeRootDirectory,
  private val extensionResourceProvider: () -> PiBundledThemeExtensionResource = ::loadBundledPiThemeExtensionResource,
  private val themeModeProvider: () -> PiThemeMode = ::detectCurrentPiThemeMode,
) {
  @Volatile
  private var materializationAttempted: Boolean = false

  @Volatile
  private var cachedLaunchResources: PiThemeLaunchResources? = null

  private val lock = Any()

  fun launchResourcesOrNull(): PiThemeLaunchResources? {
    val resources = materializedLaunchResourcesOrNull() ?: return null
    return try {
      writeCurrentThemeState(resources.stateFilePath)
      resources
    }
    catch (e: Exception) {
      THEME_LOG.warn("Failed to write Pi theme state", e)
      null
    }
  }

  fun syncCurrentThemeState() {
    val resources = cachedLaunchResources ?: return
    try {
      writeCurrentThemeState(resources.stateFilePath)
    }
    catch (e: Exception) {
      THEME_LOG.warn("Failed to sync Pi theme state", e)
    }
  }

  private fun materializedLaunchResourcesOrNull(): PiThemeLaunchResources? {
    if (materializationAttempted) return cachedLaunchResources

    synchronized(lock) {
      if (materializationAttempted) return cachedLaunchResources
      cachedLaunchResources = try {
        materializeLaunchResources()
      }
      catch (e: Exception) {
        THEME_LOG.warn("Failed to materialize Pi theme extension", e)
        null
      }
      materializationAttempted = true
      return cachedLaunchResources
    }
  }

  private fun materializeLaunchResources(): PiThemeLaunchResources {
    val extension = extensionResourceProvider()
    val rootDirectory = rootDirectoryProvider()
    val extensionDirectory = rootDirectory.resolve(PI_THEME_EXTENSION_DIRECTORY_NAME)
    val stateDirectory = rootDirectory.resolve(PI_THEME_STATE_DIRECTORY_NAME)
    val manifestPath = rootDirectory.resolve(PI_THEME_MANIFEST_FILE_NAME)

    Files.createDirectories(extensionDirectory)
    Files.createDirectories(stateDirectory)
    val currentManifest = readStringOrNull(manifestPath)
    if (currentManifest != null && !currentManifest.startsWith("formatVersion=$PI_THEME_MATERIALIZATION_FORMAT_VERSION\n")) {
      deleteRegularFiles(rootDirectory.resolve(PI_LEGACY_THEME_DIRECTORY_NAME))
      deleteRegularFiles(extensionDirectory)
    }

    val hashes = LinkedHashMap<String, String>()
    validateMaterializedFileName(extension.fileName)
    val hash = DigestUtil.sha256Hex(extension.bytes)
    hashes[extension.fileName] = hash
    val extensionPath = extensionDirectory.resolve(extension.fileName)
    if (sha256HexOrNull(extensionPath) != hash) {
      writeAtomically(extensionPath, extension.bytes)
    }

    deleteUnexpectedRegularFiles(extensionDirectory, hashes.keys)

    val expectedManifest = buildManifest(hashes)
    if (currentManifest != expectedManifest) {
      writeAtomically(manifestPath, expectedManifest.toByteArray(StandardCharsets.UTF_8))
    }

    return PiThemeLaunchResources(
      extensionPath = extensionPath,
      stateFilePath = stateDirectory.resolve(PI_THEME_STATE_FILE_NAME),
    )
  }

  private fun writeCurrentThemeState(stateFilePath: Path) {
    writeStringIfChanged(stateFilePath, themeModeProvider().stateValue + "\n")
  }

  companion object {
    val DEFAULT: PiThemeSupport = PiThemeSupport()
  }
}

internal data class PiThemeLaunchResources(
  val extensionPath: Path,
  val stateFilePath: Path,
)

internal class PiBundledThemeExtensionResource(
  val fileName: String,
  val bytes: ByteArray,
)

internal enum class PiThemeMode(val stateValue: String) {
  DARK("dark"),
  LIGHT("light"),
}

private fun detectCurrentPiThemeMode(): PiThemeMode {
  return if (EditorColorsManager.getInstance().isDarkEditor) PiThemeMode.DARK else PiThemeMode.LIGHT
}

private fun defaultPiThemeRootDirectory(): Path {
  return PathManager.getSystemDir().resolve("agent-workbench").resolve("pi-themes")
}

private fun loadBundledPiThemeExtensionResource(): PiBundledThemeExtensionResource {
  val resourcePath = "$PI_THEME_EXTENSION_RESOURCE_DIRECTORY/$PI_THEME_EXTENSION_FILE_NAME"
  val bytes = PiThemeSupport::class.java.classLoader.getResourceAsStream(resourcePath)
                ?.use { input -> input.readBytes() }
              ?: error("Missing bundled Pi theme extension resource: $resourcePath")
  return PiBundledThemeExtensionResource(PI_THEME_EXTENSION_FILE_NAME, bytes)
}

private fun validateMaterializedFileName(fileName: String) {
  require(fileName.isNotBlank()) { "Pi theme extension file name must not be blank" }
  require(Path.of(fileName).fileName.toString() == fileName) { "Pi theme extension file name must not contain path separators: $fileName" }
}

private fun buildManifest(hashes: Map<String, String>): String {
  return buildString {
    append("formatVersion=")
    append(PI_THEME_MATERIALIZATION_FORMAT_VERSION)
    append('\n')
    for ((fileName, hash) in hashes.toSortedMap()) {
      append(fileName)
      append('=')
      append(hash)
      append('\n')
    }
  }
}

private fun sha256HexOrNull(path: Path): String? {
  if (!Files.isRegularFile(path)) return null
  return runCatching { sha256Hex(path) }.getOrNull()
}

private fun readStringOrNull(path: Path): String? {
  if (!Files.isRegularFile(path)) return null
  return runCatching { EelFiles.readString(path) }.getOrNull()
}

private fun deleteUnexpectedRegularFiles(directory: Path, expectedFileNames: Set<String>) {
  if (!Files.isDirectory(directory)) return
  Files.list(directory).use { stream ->
    stream.forEach { path ->
      val fileName = path.fileName.toString()
      if (Files.isRegularFile(path) && fileName !in expectedFileNames) {
        Files.deleteIfExists(path)
      }
    }
  }
}

private fun deleteRegularFiles(directory: Path) {
  if (!Files.isDirectory(directory)) return
  Files.list(directory).use { stream ->
    stream.forEach { path ->
      if (Files.isRegularFile(path)) {
        Files.deleteIfExists(path)
      }
    }
  }
}

private fun writeStringIfChanged(path: Path, content: String) {
  if (readStringOrNull(path) == content) return
  writeAtomically(path, content.toByteArray(StandardCharsets.UTF_8))
}

private fun writeAtomically(path: Path, bytes: ByteArray) {
  path.parent?.let(Files::createDirectories)
  val tempFile = Files.createTempFile(path.parent, path.fileName.toString(), ".tmp")
  var moved = false
  try {
    Files.write(tempFile, bytes)
    try {
      Files.move(tempFile, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }
    catch (_: AtomicMoveNotSupportedException) {
      Files.move(tempFile, path, StandardCopyOption.REPLACE_EXISTING)
    }
    moved = true
  }
  finally {
    if (!moved) {
      Files.deleteIfExists(tempFile)
    }
  }
}

internal const val PI_THEME_STATE_ENVIRONMENT_VARIABLE: String = "AGENT_WORKBENCH_PI_THEME_STATE"

private const val PI_THEME_EXTENSION_RESOURCE_DIRECTORY: String = "pi-extension"
private const val PI_THEME_EXTENSION_FILE_NAME: String = "agent-workbench-theme.ts"
private const val PI_THEME_EXTENSION_DIRECTORY_NAME: String = "extension"
private const val PI_THEME_STATE_DIRECTORY_NAME: String = "state"
private const val PI_THEME_STATE_FILE_NAME: String = "current-theme.txt"
private const val PI_LEGACY_THEME_DIRECTORY_NAME: String = "themes"
private const val PI_THEME_MANIFEST_FILE_NAME: String = ".awb-theme-manifest"
private const val PI_THEME_MATERIALIZATION_FORMAT_VERSION: Int = 2
