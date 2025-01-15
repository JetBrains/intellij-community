package com.intellij.settingsSync.core

import com.intellij.ide.GeneralSettings
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.common.waitUntil
import com.intellij.testFramework.registerExtension
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.io.path.div

internal class SettingsProviderTest : SettingsSyncRealIdeTestBase() {

  private lateinit var settingsProvider: TestSettingsProvider

  @BeforeEach
  internal fun initFields() {
    settingsProvider = TestSettingsProvider()
    application.registerExtension(SettingsProvider.SETTINGS_PROVIDER_EP, settingsProvider, disposable)
  }

  @Test
  fun `settings from provider should be collected`() = timeoutRunBlockingAndStopBridge {
    val ideState = TestState("IDE value")
    settingsProvider.settings = ideState
    GeneralSettings.getInstance().initModifyAndSave {
      autoSaveFiles = false
    }

    initSettingsSync()

    val expectedContent = TestSettingsProvider().serialize(ideState)
    assertFileWithContent(expectedContent, settingsSyncStorage / ".metainfo" / settingsProvider.id / settingsProvider.fileName)

    val pushedSnapshot = remoteCommunicator.getVersionOnServer()
    assertNotNull(pushedSnapshot, "Nothing has been pushed")
    pushedSnapshot!!.assertSettingsSnapshot {
      fileState {
        GeneralSettings().withState {
          autoSaveFiles = false
        }
      }
      provided(settingsProvider.id, ideState)
    }
  }

  @Test
  fun `settings from provider changed on another client should be applied`() = timeoutRunBlockingAndStopBridge {
    val state = TestState("Server value")
    remoteCommunicator.prepareFileOnServer(settingsSnapshot {
      provided(settingsProvider.id, state)
    })


    initSettingsSync(SettingsSyncBridge.InitMode.JustInit)
    syncSettingsAndWait()

    val expectedContent = TestSettingsProvider().serialize(state)
    assertFileWithContent(expectedContent, settingsSyncStorage / ".metainfo" / settingsProvider.id / settingsProvider.fileName)
    assertEquals("Server value", settingsProvider.settings!!.property, "Settings from server were not applied")
  }

  @Test
  fun `test merge settings provider settings`() = timeoutRunBlockingAndStopBridge {
    val serverState = TestState(property = "Server value")
    remoteCommunicator.prepareFileOnServer(settingsSnapshot {
      provided(settingsProvider.id, serverState)
    })
    initSettingsSync()

    val localState = TestState(foo = "Local value")
    SettingsSyncEvents.getInstance().fireSettingsChanged(SyncSettingsEvent.IdeChange(settingsSnapshot {
      provided(settingsProvider.id, localState)
    }))

    syncSettingsAndWait()

    val expectedState = TestState(property = "Server value", foo = "Local value")
    assertFileWithContent(TestSettingsProvider().serialize(expectedState),
                          settingsSyncStorage / ".metainfo" / settingsProvider.id / settingsProvider.fileName)
    assertEquals(expectedState, settingsProvider.settings, "Settings were not applied")
  }

  private fun syncSettingsAndWait(event: SyncSettingsEvent = SyncSettingsEvent.SyncRequest) {
    SettingsSyncEvents.getInstance().fireSettingsChanged(event)
    bridge.waitForAllExecuted()
    timeoutRunBlocking {
      waitUntil("Waiting for queue to finish processing") {
        bridge.queueSize == 0
      }
    }
  }


  @Serializable
  internal data class TestState(
    var property: String? = null,
    var foo: String? = null
  )

  internal class TestSettingsProvider : SettingsProvider<TestState> {

    var settings: TestState? = null
    private val json = Json { prettyPrint = true }

    override val id: String = "test"
    override val fileName: String = "test.json"

    override fun collectCurrentSettings(): TestState? {
      return settings
    }

    override fun applyNewSettings(newSettings: TestState) {
      settings = newSettings
    }

    override fun serialize(settings: TestState): String {
      return json.encodeToString(settings)
    }

    @Throws(Exception::class)
    override fun deserialize(text: String): TestState {
      return json.decodeFromString(text)
    }

    override fun mergeStates(base: TestState?, older: TestState, newer: TestState): TestState {
      val mergedProperty = if (newer.property == null) older.property else newer.property
      val mergedFoo = if (newer.foo == null) older.foo else newer.foo
      return TestState(mergedProperty, mergedFoo)
    }
  }
}