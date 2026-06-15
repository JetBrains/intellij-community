// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.pi.sessions

// @spec community/plugins/agent-workbench/spec/sessions/agent-sessions-pi.spec.md

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.platform.eel.fs.EelFiles
import com.intellij.util.io.DigestUtil
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

private val THEME_LOG = logger<PiThemeSupport>()

internal class PiThemeSupport(
  private val rootDirectoryProvider: () -> Path = ::defaultPiThemeRootDirectory,
  private val extensionResourceProvider: () -> PiBundledExtensionResource = ::loadBundledPiExtensionResource,
  private val themeSnapshotProvider: () -> PiThemeSnapshot = PiThemeSnapshotBuilder()::buildSnapshot,
) {
  @Volatile
  private var materializationAttempted: Boolean = false

  @Volatile
  private var cachedMaterialization: PiExtensionMaterialization? = null

  private val lock = Any()

  fun launchResourcesOrNull(): PiExtensionLaunchResources? {
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
    val resources = cachedMaterialization?.launchResources ?: return
    try {
      writeCurrentThemeState(resources.stateFilePath)
    }
    catch (e: Exception) {
      THEME_LOG.warn("Failed to sync Pi theme state", e)
    }
  }

  private fun materializedLaunchResourcesOrNull(): PiExtensionLaunchResources? {
    if (materializationAttempted) return cachedMaterialization?.let(::revalidateMaterializationOrNull)

    synchronized(lock) {
      if (materializationAttempted) return cachedMaterialization?.let(::revalidateMaterializationOrNull)
      cachedMaterialization = try {
        materializeLaunchResources()
      }
      catch (e: Exception) {
        THEME_LOG.warn("Failed to materialize Pi extension", e)
        null
      }
      materializationAttempted = true
      return cachedMaterialization?.launchResources
    }
  }

  private fun materializeLaunchResources(): PiExtensionMaterialization {
    val extension = extensionResourceProvider()
    validateMaterializedExtensionFileName(extension.fileName)
    val extensionBytes = extension.bytes.copyOf()
    val hash = DigestUtil.sha256Hex(extensionBytes)
    val materializedFileName = buildContentAddressedExtensionFileName(extension.fileName, hash)
    val rootDirectory = rootDirectoryProvider()
    val extensionDirectory = rootDirectory.resolve(PI_EXTENSION_DIRECTORY_NAME)
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
    hashes[materializedFileName] = hash
    val extensionPath = extensionDirectory.resolve(materializedFileName)
    if (sha256HexOrNull(extensionPath) != hash) {
      writeAtomically(extensionPath, extensionBytes)
    }

    deleteUnexpectedRegularFiles(extensionDirectory, hashes.keys)

    val expectedManifest = buildManifest(hashes)
    if (currentManifest != expectedManifest) {
      writeAtomically(manifestPath, expectedManifest.toByteArray(StandardCharsets.UTF_8))
    }

    return PiExtensionMaterialization(
      launchResources = PiExtensionLaunchResources(
        extensionPath = extensionPath,
        stateFilePath = stateDirectory.resolve(PI_THEME_STATE_FILE_NAME),
      ),
      extensionBytes = extensionBytes,
      extensionHash = hash,
      extensionFileName = materializedFileName,
      manifestPath = manifestPath,
      expectedManifest = expectedManifest,
    )
  }

  private fun revalidateMaterializationOrNull(materialization: PiExtensionMaterialization): PiExtensionLaunchResources? {
    return try {
      val extensionPath = materialization.launchResources.extensionPath
      if (sha256HexOrNull(extensionPath) != materialization.extensionHash) {
        writeAtomically(extensionPath, materialization.extensionBytes)
      }
      deleteUnexpectedRegularFiles(extensionPath.parent, setOf(materialization.extensionFileName))
      if (readStringOrNull(materialization.manifestPath) != materialization.expectedManifest) {
        writeAtomically(materialization.manifestPath, materialization.expectedManifest.toByteArray(StandardCharsets.UTF_8))
      }
      materialization.launchResources
    }
    catch (e: Exception) {
      THEME_LOG.warn("Failed to revalidate Pi extension", e)
      null
    }
  }

  private fun writeCurrentThemeState(stateFilePath: Path) {
    writeStringIfChanged(stateFilePath, themeSnapshotProvider().toJsonString() + "\n")
  }

  companion object {
    val DEFAULT: PiThemeSupport = PiThemeSupport()
  }
}

internal data class PiExtensionLaunchResources(
  val extensionPath: Path,
  val stateFilePath: Path,
)

private class PiExtensionMaterialization(
  val launchResources: PiExtensionLaunchResources,
  val extensionBytes: ByteArray,
  val extensionHash: String,
  val extensionFileName: String,
  val manifestPath: Path,
  val expectedManifest: String,
)

internal class PiBundledExtensionResource(
  val fileName: String,
  val bytes: ByteArray,
)

private fun defaultPiThemeRootDirectory(): Path {
  return PathManager.getSystemDir().resolve("agent-workbench").resolve("pi-themes")
}

private fun loadBundledPiExtensionResource(): PiBundledExtensionResource {
  val resourcePath = "$PI_EXTENSION_RESOURCE_DIRECTORY/$PI_EXTENSION_FILE_NAME"
  val bytes = PiThemeSupport::class.java.classLoader.getResourceAsStream(resourcePath)
                ?.use { input -> input.readBytes() }
              ?: error("Missing bundled Pi extension resource: $resourcePath")
  return PiBundledExtensionResource(PI_EXTENSION_FILE_NAME, bytes)
}

private fun validateMaterializedExtensionFileName(fileName: String) {
  require(fileName.isNotBlank()) { "Pi extension file name must not be blank" }
  require(Path.of(fileName).fileName.toString() == fileName) { "Pi extension file name must not contain path separators: $fileName" }
}

private fun buildContentAddressedExtensionFileName(fileName: String, hash: String): String {
  val hashPrefix = hash.take(PI_EXTENSION_FILE_NAME_HASH_PREFIX_LENGTH)
  val extensionSeparatorIndex = fileName.lastIndexOf('.')
  return if (extensionSeparatorIndex > 0) {
    fileName.substring(0, extensionSeparatorIndex) + "-$hashPrefix" + fileName.substring(extensionSeparatorIndex)
  }
  else {
    "$fileName-$hashPrefix"
  }
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
  return runCatching {
    val digest = DigestUtil.sha256()
    DigestUtil.updateContentHash(digest, path)
    DigestUtil.digestToHash(digest)
  }.getOrNull()
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

private const val PI_EXTENSION_RESOURCE_DIRECTORY: String = "pi-extension"
private const val PI_EXTENSION_FILE_NAME: String = "agent-workbench-extension.ts"
private const val PI_EXTENSION_FILE_NAME_HASH_PREFIX_LENGTH: Int = 16
private const val PI_EXTENSION_DIRECTORY_NAME: String = "extension"
private const val PI_THEME_STATE_DIRECTORY_NAME: String = "state"
private const val PI_THEME_STATE_FILE_NAME: String = "current-theme.txt"
private const val PI_LEGACY_THEME_DIRECTORY_NAME: String = "themes"
private const val PI_THEME_MANIFEST_FILE_NAME: String = ".awb-theme-manifest"
private const val PI_THEME_MATERIALIZATION_FORMAT_VERSION: Int = 4
