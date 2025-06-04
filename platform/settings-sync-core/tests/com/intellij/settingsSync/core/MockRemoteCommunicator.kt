package com.intellij.settingsSync.core

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.io.FileUtil
import com.intellij.settingsSync.core.auth.SettingsSyncAuthService
import com.intellij.settingsSync.core.communicator.SettingsSyncCommunicatorProvider
import com.intellij.settingsSync.core.communicator.SettingsSyncUserData
import org.junit.Assert
import java.awt.Component
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.time.Instant
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.Icon
import kotlin.isInitialized

internal val MOCK_CODE = "MOCK"
internal val DUMMY_USER_ID = "dummyUserId"

internal class MockRemoteCommunicator(override val userId: String) : AbstractServerCommunicator() {
  private val filesAndVersions = mutableMapOf<String, Version>()
  private val versionIdStorage = mutableMapOf<String, String>()
  private val LOG = logger<MockRemoteCommunicator>()
  var isConnected = true
  var wasDisposed = false

  private lateinit var pushedLatch: CountDownLatch
  private lateinit var pushedSnapshot: SettingsSnapshot

  fun settingsPushed(snapshot: SettingsSnapshot) {
    if (::pushedLatch.isInitialized) {
      pushedSnapshot = snapshot
      pushedLatch.countDown()
    }
  }

  override fun requestSuccessful() {
    // do nothing
  }

  override fun handleRemoteError(e: Throwable): String {
    // do nothing yet
    return e.message ?: "unknown error"
  }

  override fun readFileInternal(filePath: String): Pair<InputStream?, String?> {
    checkConnected()
    val version = filesAndVersions[filePath] ?: throw IOException("file $filePath is not found")
    versionIdStorage.put(filePath, version.versionId)
    LOG.warn("Put version '${version.versionId}' for file $filePath (after read)")
    return Pair(ByteArrayInputStream(version.content), version.versionId)
  }

  override fun writeFileInternal(filePath: String, versionId: String?, content: InputStream): String? {
    checkConnected()
    val currentVersion = filesAndVersions[filePath]
    if (versionId != null && currentVersion != null && currentVersion.versionId != versionId) {
      throw InvalidVersionIdException("Expected version $versionId, but actual is ${currentVersion.versionId}")
    }
    val version = Version(content.readAllBytes())
    filesAndVersions[filePath] = version
    versionIdStorage.put(filePath, version.versionId);
    LOG.warn("Put version '${version.versionId}' for file $filePath (after write)")
    return version.versionId
  }

  override fun getLatestVersion(filePath: String): String? {
    checkConnected()
    val version = filesAndVersions[filePath] ?: return null
    return version.versionId
  }

  override fun deleteFileInternal(filePath: String) {
    checkConnected()
    filesAndVersions - filePath
    versionIdStorage.remove(filePath)
    LOG.warn("Removed version for file $filePath")
  }

  fun awaitForPush(testExecution: () -> Unit): SettingsSnapshot {
    pushedLatch = CountDownLatch(1)
    testExecution()
    Assert.assertTrue("Didn't await until changes are pushed", pushedLatch.wait())
    return pushedSnapshot
  }

  override fun push(snapshot: SettingsSnapshot, force: Boolean, expectedServerVersionId: String?): SettingsSyncPushResult {
    val push = super.push(snapshot, force, expectedServerVersionId)
    settingsPushed(snapshot)
    return push
  }

  fun prepareFileOnServer(snapshot: SettingsSnapshot) {
    ByteArrayOutputStream().use { stream ->
      SettingsSnapshotZipSerializer.serializeToStream(snapshot, stream)
      val content = stream.toByteArray()
      val (snapshotFilePath, _) = currentSnapshotFilePath() ?: return
      versionIdStorage.remove(snapshotFilePath)
      filesAndVersions.remove(snapshotFilePath)
      writeFileInternal(snapshotFilePath, System.nanoTime().toString(), ByteArrayInputStream(content))
    }
  }
  private fun getSnapshotFromVersion(version: ByteArray?): SettingsSnapshot? {
    if (version == null) {
      return null
    }
    val tempFile = FileUtil.createTempFile(UUID.randomUUID().toString(), null)
    FileUtil.writeToFile(tempFile, version)
    return SettingsSnapshotZipSerializer.extractFromZip(tempFile.toPath())
  }

  fun getVersionOnServer(): SettingsSnapshot? {
    val (snapshotFilePath, _) = currentSnapshotFilePath() ?: return null
    return getSnapshotFromVersion(filesAndVersions[snapshotFilePath]?.content)
  }

  fun deleteAllFiles() {
    filesAndVersions.clear()
  }

  fun ideCrossSyncState(): Boolean? {
    val (_, crossSyncState) = currentSnapshotFilePath() ?: Pair(null, null)

    return crossSyncState
  }


  private class Version(val content: ByteArray, val versionId: String) {
    constructor(content: ByteArray) : this(content, System.nanoTime().toString())
  }

  private fun checkConnected() {
    if (!isConnected) {
      throw IOException(DISCONNECTED_ERROR)
    }
  }

  override fun dispose() {
    LOG.info("Disposing...")
    wasDisposed = true
  }

  companion object {
    private val versionRef = AtomicInteger()
    val snapshotForDeletion =
      SettingsSnapshot(SettingsSnapshot.MetaInfo(Instant.now(), getLocalApplicationInfo(), isDeleted = true), emptySet(), null, emptyMap(), emptySet())
    const val DISCONNECTED_ERROR = "disconnected"
  }
}

internal class MockCommunicatorProvider (
  private val remoteCommunicator: SettingsSyncRemoteCommunicator,
  override val authService: SettingsSyncAuthService,
): SettingsSyncCommunicatorProvider {
  override val providerCode: String
    get() = MOCK_CODE

  override fun createCommunicator(userId: String): SettingsSyncRemoteCommunicator? = remoteCommunicator
}

internal class MockAuthService (
  internal var userData: SettingsSyncUserData?
): SettingsSyncAuthService {
  override val providerCode: String
    get() = MOCK_CODE
  override val providerName: String
    get() = TODO("Not yet implemented")
  override val icon: Icon?
    get() = TODO("Not yet implemented")

  override suspend fun login(parentComponent: Component?) : SettingsSyncUserData? {
    return null
  }

  override fun getUserData(userId: String): SettingsSyncUserData? {
    return userData
  }

  override fun getAvailableUserAccounts(): List<SettingsSyncUserData> {
    TODO("Not yet implemented")
  }

}