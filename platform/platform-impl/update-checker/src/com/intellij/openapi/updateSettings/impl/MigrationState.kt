// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.impl.ApplicationInfoImpl
import com.intellij.openapi.diagnostic.logger
import com.intellij.platform.ide.productInfo.IdeProductInfo
import com.intellij.util.io.PreemptiveSafeFileOutputStream
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.annotations.ApiStatus
import java.io.IOException
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import kotlin.io.path.isDirectory

private val LOG = logger<MigrationState>()

private const val STATE_FILE_NAME = "migration.json"
private const val FORMAT_VERSION = 1

@ApiStatus.Internal
enum class MigrationStatus(val id: String) {
  /** The Unified IDE is installed. */
  INSTALLED("installed"),

  /** The IDE started the Unified IDE and exited. */
  MOVED("moved"),
}

/** The state of the migration. [app] is the absolute path of the Unified IDE. [product] is the product code of the distribution. */
@ApiStatus.Internal
class MigrationState(val status: MigrationStatus, val app: Path, val product: String) {
  fun withStatus(status: MigrationStatus): MigrationState = MigrationState(status, app, product)
}

private sealed interface MigrationStateParseResult {
  class Valid(val state: MigrationState) : MigrationStateParseResult

  class Invalid(val reason: String) : MigrationStateParseResult
}

/** The state file is a plain file in the config dir, because the startup code reads it before the services exist. */
@ApiStatus.Internal
fun migrationStateFile(configDir: Path = PathManager.getConfigDir()): Path = configDir.resolve(STATE_FILE_NAME)

/** Parses the content of the state file. Unknown fields are ignored. The file system is not checked. */
private fun parseMigrationState(text: String): MigrationStateParseResult {
  val dto = try {
    json.decodeFromString<MigrationStateDto>(text)
  }
  catch (e: IllegalArgumentException) {
    return MigrationStateParseResult.Invalid("malformed JSON: ${e.message}")
  }

  if (dto.version != FORMAT_VERSION) return MigrationStateParseResult.Invalid("unsupported 'version': ${dto.version}")
  val status = MigrationStatus.entries.find { it.id == dto.status }
               ?: return MigrationStateParseResult.Invalid("unknown 'status': ${dto.status}")
  val appPath = dto.app?.takeIf { it.isNotBlank() } ?: return MigrationStateParseResult.Invalid("no 'app'")
  val app = try {
    Path.of(appPath)
  }
  catch (_: IllegalArgumentException) {
    return MigrationStateParseResult.Invalid("invalid 'app': ${appPath}")
  }
  if (!app.isAbsolute) return MigrationStateParseResult.Invalid("'app' is not absolute: ${appPath}")
  val product = dto.product?.takeIf { it.isNotBlank() } ?: return MigrationStateParseResult.Invalid("no 'product'")
  return MigrationStateParseResult.Valid(MigrationState(status, app, product))
}

private fun formatMigrationState(state: MigrationState): String =
  json.encodeToString(MigrationStateDto(FORMAT_VERSION, state.status.id, state.app.toString(), state.product))

/**
 * Returns the state in [file], or `null` when the file does not exist.
 * A valid state is not about this IDE, and its Unified IDE exists and has the product code [MigrationState.product].
 * An invalid file is deleted, and the log gets the reason.
 */
@ApiStatus.Internal
fun readMigrationState(file: Path, productCodeOf: (Path) -> String? = ::installedProductCode): MigrationState? {
  val text = try {
    Files.readString(file)
  }
  catch (_: NoSuchFileException) {
    return null
  }
  catch (e: IOException) {
    LOG.warn("cannot read ${file}", e)
    return null
  }

  val state = when (val result = parseMigrationState(text)) {
    is MigrationStateParseResult.Valid -> result.state
    is MigrationStateParseResult.Invalid -> return discard(file, result.reason)
  }
  if (state.product == ApplicationInfoImpl.getShadowInstance().build.productCode) {
    return discard(file, "the state is about this IDE: ${state.product}")
  }
  if (!state.app.isDirectory()) {
    return discard(file, "the Unified IDE does not exist: ${state.app}")
  }
  val productCode = productCodeOf(state.app)
  if (productCode != state.product) {
    return discard(file, "${state.app} has the product code ${productCode}, expected ${state.product}")
  }
  return state
}

/** Writes a temporary file next to [file], then renames it. */
@ApiStatus.Internal
@Throws(IOException::class)
fun writeMigrationState(state: MigrationState, file: Path) {
  Files.createDirectories(file.parent)
  PreemptiveSafeFileOutputStream(file).use { it.write(formatMigrationState(state).toByteArray(Charsets.UTF_8)) }
}

/** Keeps the state of [file] in memory. The first [get] reads the file. */
@ApiStatus.Internal
class MigrationStateStore(private val file: Path, private val productCodeOf: (Path) -> String? = ::installedProductCode) {
  private val lock = Any()
  private var isLoaded = false
  private var state: MigrationState? = null

  fun get(): MigrationState? = synchronized(lock) {
    if (!isLoaded) {
      load()
    }
    state
  }

  /** Deletes the file, for example when the Unified IDE is removed while the IDE runs. */
  fun clear() {
    synchronized(lock) {
      state = null
      isLoaded = true
      deleteLogged(file)
    }
  }

  /** Keeps [newState] in memory also when the write fails, so that the session works with it. */
  @Throws(IOException::class)
  fun set(newState: MigrationState) {
    synchronized(lock) {
      state = newState
      isLoaded = true
      writeMigrationState(newState, file)
    }
  }

  private fun load() {
    state = readMigrationState(file, productCodeOf)
    isLoaded = true
  }
}

private val defaultStore by lazy { MigrationStateStore(migrationStateFile()) }

/** The state of the config dir of this IDE. */
internal fun migrationStateStore(): MigrationStateStore = defaultStore

private fun installedProductCode(app: Path): String? = IdeProductInfo.getInstance().loadProductInfo(app)?.productCode

private fun discard(file: Path, reason: String): MigrationState? {
  LOG.info("the migration state is ignored and deleted: ${reason}")
  deleteLogged(file)
  return null
}

private fun deleteLogged(file: Path) {
  try {
    Files.deleteIfExists(file)
  }
  catch (e: IOException) {
    LOG.warn("cannot delete ${file}", e)
  }
}

private val json = Json { ignoreUnknownKeys = true }

@Serializable
private class MigrationStateDto(
  val version: Int? = null,
  val status: String? = null,
  val app: String? = null,
  val product: String? = null,
)
