@file:OptIn(IntellijInternalApi::class)

package com.intellij.settingsSync.core

import com.intellij.ide.plugins.PluginManagerCore.VENDOR_JETBRAINS
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.impl.ApplicationImpl
import com.intellij.openapi.components.ComponentManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.DefaultPluginDescriptor
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.settingsSync.core.communicator.*
import com.intellij.testFramework.common.DEFAULT_TEST_TIMEOUT
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.util.io.createDirectories
import com.intellij.util.io.write
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.time.Duration

internal val TIMEOUT_UNIT = TimeUnit.SECONDS

private val LOG = logger<SettingsSyncTestBase>()

@TestApplication
internal abstract class SettingsSyncTestBase {

  protected lateinit var application: ApplicationImpl
  protected lateinit var configDir: Path
  protected lateinit var remoteCommunicator: MockRemoteCommunicator
  protected lateinit var authService: MockAuthService
  protected lateinit var updateChecker: SettingsSyncUpdateChecker
  protected lateinit var bridge: SettingsSyncBridge

  @TestDisposable
  protected lateinit var disposable: Disposable
  protected val settingsSyncStorage: Path get() = configDir.resolve("settingsSync")

  @BeforeEach
  fun setup(@TempDir mainDir: Path) {
    application = ApplicationManager.getApplication() as ApplicationImpl
    configDir = mainDir.resolve("rootconfig").createDirectories()

    SettingsSyncLocalSettings.getInstance().state.reset()
    SettingsSyncSettings.getInstance().state = SettingsSyncSettings.State()

    remoteCommunicator = if (isTestingAgainstRealCloudServer()) {
      TODO("Implement with real server via TestRemoteCommunicator()")
    }
    else {
      MockRemoteCommunicator("mockUser").apply {this.isConnected = true  }
    }
    val providerEP = getSyncProviderPoint()
    if (providerEP.extensions.size > 0) {
      LOG.warn("SettingsSyncCommunicatorProvider.PROVIDER_EP is not empty: ${providerEP.extensions.toList()}")
      for (it in providerEP.extensions) {
        LOG.warn("Unregistering extension: ${it.instance.javaClass.name}")
        providerEP.unregisterExtension(it)
      }
    }

    authService = MockAuthService(SettingsSyncUserData("mockId", MOCK_CODE, "", ""))

    val mockCommunicatorProvider = MockCommunicatorProvider(
      remoteCommunicator,
      authService
    )
    providerEP.registerExtension(object : SettingsSyncCommunicatorBean() {
      init {
        this.pluginDescriptor = DefaultPluginDescriptor(
          PluginId.getId("com.intellij.settingsSync"),
          SettingsSyncTestBase::class.java.getClassLoader(),
          VENDOR_JETBRAINS
        )
      }

      override fun createInstance(
        componentManager: ComponentManager,
        pluginDescriptor: PluginDescriptor,
      ): SettingsSyncCommunicatorProvider = mockCommunicatorProvider
    }, disposable)

    SettingsSyncLocalSettings.getInstance().providerCode = mockCommunicatorProvider.providerCode
    SettingsSyncLocalSettings.getInstance().userId = DUMMY_USER_ID

    val serverState = remoteCommunicator.checkServerState()
    if (serverState != ServerState.FileNotExists) {
      LOG.warn("Server state: $serverState")
      remoteCommunicator.deleteAllFiles()
    }
  }

  @AfterEach
  fun cleanup() {
    remoteCommunicator.deleteAllFiles()
    if (::bridge.isInitialized) {
      runBlocking {
        bridge.waitForAllExecuted()
      }
      bridge.stop()
    }
    RemoteCommunicatorHolder.invalidateCommunicator()
  }

  protected fun <T> timeoutRunBlockingAndStopBridge(
    timeout: Duration = DEFAULT_TEST_TIMEOUT,
    coroutineName: String? = null,
    action: suspend CoroutineScope.() -> T,
  ): T {
    return timeoutRunBlocking(timeout, coroutineName, context = Dispatchers.Default) {
      val retval = action()
      cleanup()
      retval
    }
  }


  protected fun writeToConfig(build: SettingsSnapshotBuilder.() -> Unit) {
    val builder = SettingsSnapshotBuilder()
    builder.build()
    for (file in builder.fileStates) {
      file as FileState.Modified
      configDir.resolve(file.file).write(file.content)
    }
  }

  protected fun assertFileWithContent(expectedContent: String, file: Path) {
    assertTrue(file.exists(), "File $file does not exist")
    assertEquals(expectedContent, file.readText(), "File $file has unexpected content")
  }

  protected fun assertIdeCrossSync(expectedIdeCrossSyncState: Boolean?) {
    val actualIdeCrossSyncState = remoteCommunicator.ideCrossSyncState()

    assertEquals(expectedIdeCrossSyncState, actualIdeCrossSyncState, "Unexpected IDE cross sync state $actualIdeCrossSyncState, expected $actualIdeCrossSyncState")
  }

  protected fun assertServerSnapshot(build: SettingsSnapshotBuilder.() -> Unit) {
    val pushedSnapshot = remoteCommunicator.getVersionOnServer()
    assertNotNull(pushedSnapshot, "Nothing has been pushed")
    pushedSnapshot!!.assertSettingsSnapshot {
      build()
    }
  }

  protected suspend fun executeAndWaitUntilPushed(testExecution: suspend () -> Unit): SettingsSnapshot {
    val snapshot = remoteCommunicator.awaitForPush {
      testExecution()
      bridge.waitForAllExecuted()
    }
    return snapshot
  }
}

internal fun CountDownLatch.wait(): Boolean {
  return this.await(getDefaultTimeoutInSeconds(), TIMEOUT_UNIT)
}

private fun isTestingAgainstRealCloudServer() = System.getenv("SETTINGS_SYNC_TEST_CLOUD") == "real"

internal fun getDefaultTimeoutInSeconds(): Long = 2
