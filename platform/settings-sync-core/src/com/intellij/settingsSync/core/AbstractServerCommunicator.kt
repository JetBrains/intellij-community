package com.intellij.settingsSync.core

import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.io.delete
import org.jetbrains.annotations.VisibleForTesting
import java.io.IOException
import java.io.InputStream
import java.util.UUID
import kotlin.io.path.inputStream

abstract class AbstractServerCommunicator() : SettingsSyncRemoteCommunicator {
  companion object {
    private val LOG = logger<AbstractServerCommunicator>()
  }

  private var myTemporary = false

  override fun setTemporary(isTemporary: Boolean) {
    myTemporary = isTemporary
  }

  /**
   * called when a request is successful, as an opposite to handleRemoteError
   */
  protected open fun requestSuccessful() {}

  /**
   * Handles errors that occur during remote operations by mapping the given `Throwable` to a meaningful error message.
   *
   * @param e The exception or error that occurred during a remote operation.
   * @return A string describing the error in a human-readable format, suitable for logging or display.
   */
  protected abstract fun handleRemoteError(e: Throwable): String

  /**
   * Reads the content of a file from the given file path.
   *
   * @param filePath The path of the file to be read.
   * @return A pair containing:
   *         - An InputStream of the file content if the operation is successful, otherwise null.
   *         - A server version identifier associated with the file, or null if unavailable.
   * @throws IOException If an I/O error occurs during file reading.
   */
  @Throws(IOException::class)
  protected abstract fun readFileInternal(filePath: String): Pair<InputStream?, String?>

  /**
   * Writes the content to a specified file, potentially associated with a particular version ID.
   *
   * @param filePath The path to the file where the content will be written.
   * @param versionId An optional version identifier for the file. If provided, the version ID must match
   *                  the version on server; otherwise, an InvalidVersionIdException may occur.
   * @param content The content to be written to the file, provided as an InputStream.
   * @return The version ID of the file after the content is written, or null if the operation does not result
   *         in an identifiable version.
   * @throws IOException If an I/O error occurs during the writing process.
   * @throws InvalidVersionIdException If the provided(expected) version ID doesn't match the actual remote one.
   */
  @Throws(IOException::class, InvalidVersionIdException::class)
  protected abstract fun writeFileInternal(filePath: String, versionId: String?, content: InputStream): String?

  /**
   * Fetches the latest version identifier for the file at the specified file path.
   *
   * This version is compared against SettingsSyncLocalSettings.getKnownAndAppliedServerId
   * to check whether settings version on server is different from the local one.
   *
   * @param filePath The path to the file whose latest version is to be retrieved.
   * @return The latest version identifier of the file, or null if no version information is available.
   * @throws IOException If an I/O error occurs while attempting to retrieve the version information.
   */
  @Throws(IOException::class)
  protected abstract fun getLatestVersion(filePath: String) : String?

  @Throws(IOException::class)
  protected abstract fun deleteFileInternal(filePath: String)


  @VisibleForTesting
  @Throws(IOException::class, SecurityException::class)
  protected fun currentSnapshotFilePath(): Pair<String, Boolean>? {
    try {
      val crossIdeSyncEnabled = isFileExists(CROSS_IDE_SYNC_MARKER_FILE)
      if (!myTemporary && crossIdeSyncEnabled != SettingsSyncLocalSettings.getInstance().isCrossIdeSyncEnabled) {
        LOG.info("Cross-IDE sync status on server is: ${enabledOrDisabled(crossIdeSyncEnabled)}. Updating local settings with it.")
        SettingsSyncLocalSettings.getInstance().isCrossIdeSyncEnabled = crossIdeSyncEnabled
      }
      if (crossIdeSyncEnabled) {
        return Pair(SETTINGS_SYNC_SNAPSHOT_ZIP, true)
      }
      else {
        return Pair("${ApplicationNamesInfo.getInstance().productName.lowercase()}/$SETTINGS_SYNC_SNAPSHOT_ZIP", false)
      }
    }
    catch (e: Throwable) {
      if (e is IOException || e is SecurityException) {
        throw e
      }
      else {
        LOG.warn("Couldn't check if $CROSS_IDE_SYNC_MARKER_FILE exists", e)
        return null
      }
    }
  }


  @VisibleForTesting
  internal fun sendSnapshotFile(
    inputStream: InputStream,
    knownServerVersion: String?,
    force: Boolean,
  ): SettingsSyncPushResult {
    val snapshotFilePath: String
    val defaultMessage = "Unknown during checking $CROSS_IDE_SYNC_MARKER_FILE"
    try {
      snapshotFilePath = currentSnapshotFilePath()?.first ?: return SettingsSyncPushResult.Error(defaultMessage)

      val versionToPush: String?
      if (force) {
        // get the latest server version: pushing with it will overwrite the file in any case
        versionToPush = getLatestVersion(snapshotFilePath)
      }
      else {
        if (knownServerVersion != null) {
          versionToPush = knownServerVersion
        }
        else {
          val serverVersion = getLatestVersion(snapshotFilePath)
          if (serverVersion == null) {
            // no file on the server => just push it there
            versionToPush = null
          }
          else {
            // we didn't store the server version locally yet => reject the push to avoid overwriting the server version;
            // the next update after the rejected push will store the version information, and subsequent push will be successful.
            return SettingsSyncPushResult.Rejected
          }
        }
      }

      val pushedVersion = writeFileInternal(snapshotFilePath, versionToPush, inputStream)
      // errors are thrown as exceptions, and are handled above
      return SettingsSyncPushResult.Success(pushedVersion)
    }
    catch (e: Throwable) {
      return SettingsSyncPushResult.Error(e.message ?: defaultMessage)
    }
  }

  override fun checkServerState(): ServerState {
    try {
      val snapshotFilePath = currentSnapshotFilePath()?.first ?: return ServerState.Error("Unknown error during checkServerState")
      val latestVersion = getLatestVersion(snapshotFilePath)
      LOG.debug("Latest version info: $latestVersion")
      requestSuccessful()
      when (latestVersion) {
        null -> return ServerState.FileNotExists
        SettingsSyncLocalSettings.getInstance().knownAndAppliedServerId -> return ServerState.UpToDate
        else -> return ServerState.UpdateNeeded
      }
    }
    catch (e: Throwable) {
      val message = handleRemoteError(e)
      return ServerState.Error(message)
    }
  }

  override fun receiveUpdates(): UpdateResult {
    LOG.info("Receiving settings snapshot from the cloud config server...")
    try {
      val (snapshotFilePath, isCrossIdeSync) = currentSnapshotFilePath() ?: return UpdateResult.Error("Unknown error during receiveUpdates")
      val (stream, version) = readFileInternal(snapshotFilePath)
      requestSuccessful()
      if (stream == null) {
        LOG.info("$snapshotFilePath not found on the server")
        return UpdateResult.NoFileOnServer
      }

      val tempFile = FileUtil.createTempFile(SETTINGS_SYNC_SNAPSHOT, UUID.randomUUID().toString() + ".zip")
      try {
        FileUtil.writeToFile(tempFile, stream.readAllBytes())
        val snapshot = SettingsSnapshotZipSerializer.extractFromZip(tempFile.toPath())
        if (snapshot == null) {
          LOG.info("cannot extract snapshot from tempFile ${tempFile.toPath()}. Implying there's no snapshot")
          return UpdateResult.NoFileOnServer
        }
        else {
          return if (snapshot.isDeleted()) UpdateResult.FileDeletedFromServer else UpdateResult.Success(snapshot, version, isCrossIdeSync)
        }
      }
      finally {
        FileUtil.delete(tempFile)
      }
    }
    catch (e: Throwable) {
      val message = handleRemoteError(e)
      return UpdateResult.Error(message)
    }
  }

  override fun push(snapshot: SettingsSnapshot, force: Boolean, expectedServerVersionId: String?): SettingsSyncPushResult {
    LOG.info("Pushing setting snapshot to the cloud config server...")
    val zip = try {
      SettingsSnapshotZipSerializer.serializeToZip(snapshot)
    }
    catch (e: Throwable) {
      LOG.warn(e)
      return SettingsSyncPushResult.Error(e.message ?: "Couldn't prepare zip file")
    }

    try {
      val pushResult = sendSnapshotFile(zip.inputStream(), expectedServerVersionId, force)
      requestSuccessful()
      return pushResult
    }
    catch (ive: InvalidVersionIdException) {
      LOG.info("Rejected: version doesn't match the version on server: ${ive.message}")
      return SettingsSyncPushResult.Rejected
    }
    catch (e: Throwable) {
      val message = handleRemoteError(e)
      return SettingsSyncPushResult.Error(message)
    }
    finally {
      try {
        zip.delete()
      }
      catch (e: Throwable) {
        LOG.warn(e)
      }
    }
  }

  override fun createFile(filePath: String, content: String) {
    writeFileInternal(filePath, null, content.byteInputStream())
  }

  @Throws(IOException::class)
  override fun deleteFile(filePath: String) {
    SettingsSyncLocalSettings.getInstance().knownAndAppliedServerId = null
    deleteFileInternal(filePath)
  }

  @Throws(IOException::class)
  override fun isFileExists(filePath: String): Boolean {
    return getLatestVersion(filePath) != null
  }
}

class InvalidVersionIdException(override val message: String, override val cause: Throwable? = null) : RuntimeException(message, cause) {}