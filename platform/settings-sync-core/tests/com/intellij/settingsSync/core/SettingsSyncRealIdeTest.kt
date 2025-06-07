package com.intellij.settingsSync.core

import com.intellij.configurationStore.getPerOsSettingsStorageFolderName
import com.intellij.ide.GeneralSettings
import com.intellij.ide.fileTemplates.FileTemplateManager
import com.intellij.ide.ui.UISettings
import com.intellij.idea.TestFor
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.*
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable
import com.intellij.openapi.keymap.impl.KeymapImpl
import com.intellij.openapi.keymap.impl.KeymapManagerImpl
import com.intellij.openapi.util.Disposer
import com.intellij.settingsSync.core.SettingsSnapshot.MetaInfo
import com.intellij.util.toByteArray
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.nio.charset.Charset
import java.time.Instant
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.time.Duration.Companion.seconds

internal class SettingsSyncRealIdeTest : SettingsSyncRealIdeTestBase() {

  @Test
  fun `settings are pushed`() = timeoutRunBlockingAndStopBridge {
    SettingsSyncSettings.getInstance().init()
    SettingsSyncSettings.getInstance().migrationFromOldStorageChecked = true
    saveComponentStore()
    initSettingsSync(SettingsSyncBridge.InitMode.PushToServer)

    executeAndWaitUntilPushed {
      GeneralSettings.getInstance().initModifyAndSave {
        autoSaveFiles = false
      }
    }

    assertServerSnapshot {
      fileState {
        GeneralSettings().withState {
          autoSaveFiles = false
        }
      }
      fileState(SettingsSyncSettings.getInstance().toFileState())
    }
  }

  @Test
  fun `scheme changes are logged`() = timeoutRunBlockingAndStopBridge {
    initSettingsSync(SettingsSyncBridge.InitMode.JustInit)

    val keymap = createKeymap()
    executeAndWaitUntilPushed {
      saveComponentStore()
    }

    assertServerSnapshot {
      fileState("keymaps/${keymap.name}.xml", String(keymap.writeScheme().toByteArray(), Charset.defaultCharset()))
    }
  }

  @Test
  @TestFor(issues = ["IJPL-175567"])
  fun `non-scheme additionalExportDirectory changes are logged`() = timeoutRunBlockingAndStopBridge {
    initSettingsSync(SettingsSyncBridge.InitMode.JustInit)

    val fileTemplate = FileTemplateManager.getDefaultInstance().addTemplate("my-mega-template", "php")
    Disposer.register(disposable, Disposable {
      FileTemplateManager.getDefaultInstance().removeTemplate(fileTemplate)
    })
    val phpCode = "<?php echo 'hello world' ?>"
    fileTemplate.text= phpCode
    FileTemplateManager.getDefaultInstance().saveAllTemplates()
    executeAndWaitUntilPushed {
      saveComponentStore()
    }

    assertServerSnapshot {
      fileState("fileTemplates/my-mega-template.php", phpCode)
    }
  }

  private fun createKeymap(): KeymapImpl {
    val keymap = KeymapImpl()
    keymap.name = "SettingsSyncTestKeyMap"
    val keymapSchemeManager = KeymapManagerImpl().schemeManager
    keymapSchemeManager.addScheme(keymap, false)
    Disposer.register(disposable, Disposable {
      keymapSchemeManager.removeScheme(keymap)
    })
    return keymap
  }

  private fun saveComponentStore() {
    runBlocking { componentStore.save() }
  }

  @Test
  fun `quickly modified settings are pushed together`() = timeoutRunBlockingAndStopBridge {
    initSettingsSync(SettingsSyncBridge.InitMode.JustInit)

    GeneralSettings.getInstance().initModifyAndSave {
      autoSaveFiles = false
    }
    EditorSettingsExternalizable.getInstance().initModifyAndSave {
      SHOW_INTENTION_BULB = false
    }

    val pushedSnapshot = executeAndWaitUntilPushed {}

    pushedSnapshot.assertSettingsSnapshot {
      fileState {
        GeneralSettings().withState {
          autoSaveFiles = false
        }
      }
      fileState {
        EditorSettingsExternalizable().withState {
          SHOW_INTENTION_BULB = false
        }
      }
    }
  }

  @Test
  fun `existing settings are copied on initialization`() = timeoutRunBlockingAndStopBridge {
    GeneralSettings.getInstance().initModifyAndSave {
      autoSaveFiles = false
    }

    initSettingsSync(SettingsSyncBridge.InitMode.JustInit)

    executeAndWaitUntilPushed {
      UISettings.getInstance().initModifyAndSave {
        recentFilesLimit = 1000
      }
    }

    assertServerSnapshot {
      fileState {
        GeneralSettings().withState {
          autoSaveFiles = false
        }
      }
      fileState {
        UISettings().withState {
          recentFilesLimit = 1000
        }
      }
    }
  }

  @Test
  fun `disabled categories should be ignored when copying settings on initialization`() = timeoutRunBlockingAndStopBridge {
    GeneralSettings.getInstance().initModifyAndSave {
      autoSaveFiles = false
    }
    EditorSettingsExternalizable.getInstance().initModifyAndSave {
      SHOW_INTENTION_BULB = false
    }
    //AppEditorFontOptions.getInstance().initModifyAndSave {
    //  FONT_SIZE = FontPreferences.DEFAULT_FONT_SIZE - 5
    //}
    val keymap = createKeymap()
    saveComponentStore()

    val os = getPerOsSettingsStorageFolderName()
    SettingsSyncSettings.getInstance().setCategoryEnabled(SettingsCategory.KEYMAP, false)
    SettingsSyncSettings.getInstance().setCategoryEnabled(SettingsCategory.SYSTEM, false)
    //SettingsSyncSettings.getInstance().setSubcategoryEnabled(SettingsCategory.UI, EDITOR_FONT_SUBCATEGORY_ID,  false)

    initSettingsSync(SettingsSyncBridge.InitMode.JustInit)

    Assertions.assertTrue((settingsSyncStorage / "options" / "editor.xml").exists(), "Settings from enabled category was not copied")
    Assertions.assertFalse((settingsSyncStorage / "options" / "ide.general.xml").exists(), "Settings from disabled category was copied")
    //assertFalse("Settings from disabled subcategory was copied", (settingsSyncStorage / "options" / "editor-font.xml").exists())
    Assertions.assertFalse((settingsSyncStorage / "options" / os / "keymap.xml").exists(), "Settings from disabled category was copied")
    Assertions.assertFalse((settingsSyncStorage / "keymaps" / "${keymap.name}.xml").exists(), "Schema from disabled category was copied")
    remoteCommunicator.getVersionOnServer()!!.assertSettingsSnapshot {
      fileState {
        EditorSettingsExternalizable().withState {
          SHOW_INTENTION_BULB = false
        }
      }
    }
  }

  @Test
  fun `settings from server are applied`() = timeoutRunBlockingAndStopBridge(5.seconds) {
    val generalSettings = GeneralSettings.getInstance().init()
    initSettingsSync(SettingsSyncBridge.InitMode.JustInit)

    val fileState = GeneralSettings().apply {
      isSaveOnFrameDeactivation = false
    }.toFileState()
    remoteCommunicator.prepareFileOnServer(SettingsSnapshot(MetaInfo(Instant.now(), getLocalApplicationInfo()),
                                                            setOf(fileState), null, emptyMap(), emptySet()))

    waitForSettingsToBeApplied(generalSettings) {
      SettingsSyncEvents.getInstance().fireSettingsChanged(SyncSettingsEvent.SyncRequest)
    }
    Assertions.assertFalse(generalSettings.isSaveOnFrameDeactivation)
    bridge.waitForAllExecuted()
  }

  @Test
  fun `enabling category should copy existing settings from that category`() = timeoutRunBlockingAndStopBridge {
    SettingsSyncSettings.getInstance().setCategoryEnabled(SettingsCategory.CODE, isEnabled = false)
    GeneralSettings.getInstance().initModifyAndSave {
      autoSaveFiles = false
    }
    EditorSettingsExternalizable.getInstance().initModifyAndSave {
      SHOW_INTENTION_BULB = false
    }
    val editorXmlContent = (configDir / "options" / "editor.xml").readText()
    initSettingsSync(SettingsSyncBridge.InitMode.PushToServer)
    Assertions.assertFalse((settingsSyncStorage / "options" / "editor.xml").exists(), "editor.xml should not be synced if the Code category is disabled")
    assertServerSnapshot {
      fileState {
        GeneralSettings().withState {
          autoSaveFiles = false
        }
      }
    }

    SettingsSyncSettings.getInstance().setCategoryEnabled(SettingsCategory.CODE, isEnabled = true)
    SettingsSyncEvents.getInstance().fireSettingsChanged(SyncSettingsEvent.LogCurrentSettings)
    bridge.waitForAllExecuted()

    Assertions.assertTrue((settingsSyncStorage / "options" / "editor.xml").exists(), "editor.xml should be synced after enabling the Code category")
    assertFileWithContent(editorXmlContent, (settingsSyncStorage / "options" / "editor.xml"))
    assertServerSnapshot {
      fileState {
        GeneralSettings().withState {
          autoSaveFiles = false
        }
      }
      fileState {
        EditorSettingsExternalizable.getInstance().withState {
          SHOW_INTENTION_BULB = false
        }
      }
    }
  }

  @Test
  fun `not enabling cross IDE sync initially works as expected`() = timeoutRunBlockingAndStopBridge {
    SettingsSyncSettings.getInstance().init()
    GeneralSettings.getInstance().initModifyAndSave { autoSaveFiles = false }

    assertIdeCrossSync(false)

    initSettingsSync(SettingsSyncBridge.InitMode.PushToServer)

    assertIdeCrossSync(false)

    assertServerSnapshot {
      fileState {
        GeneralSettings().withState { autoSaveFiles = false }
      }
      fileState {
        SettingsSyncSettings().also { it.syncEnabled = true }
      }
    }
  }

  @Test
  fun `enabling cross IDE sync initially works as expected`() = timeoutRunBlockingAndStopBridge {
    SettingsSyncSettings.getInstance().init()
    GeneralSettings.getInstance().initModifyAndSave { autoSaveFiles = false }

    assertIdeCrossSync(false)

    initSettingsSync(SettingsSyncBridge.InitMode.PushToServer, true)

    assertIdeCrossSync(true)

    assertServerSnapshot {
      fileState {
        GeneralSettings().withState { autoSaveFiles = false }
      }
      fileState {
        SettingsSyncSettings().also { it.syncEnabled = true }
      }
    }
  }

  @Test
  fun `sync settings are always uploaded even if system settings are disabled`() = timeoutRunBlockingAndStopBridge {
    SettingsSyncSettings.getInstance().init()
    GeneralSettings.getInstance().initModifyAndSave { autoSaveFiles = false }

    SettingsSyncSettings.getInstance().setCategoryEnabled(SettingsCategory.SYSTEM, false)

    initSettingsSync(SettingsSyncBridge.InitMode.PushToServer, false)

    assertServerSnapshot {
      // the state of `GeneralSettings` should be explicitly absent
      fileState {
        SettingsSyncSettings().also { it.syncEnabled = true; it.setCategoryEnabled(SettingsCategory.SYSTEM, false) }
      }
    }
  }

  @Test
  fun `exportable non-roamable settings should not be synced`() = timeoutRunBlockingAndStopBridge {
    testVariousComponentsShouldBeSyncedOrNot(ExportableNonRoamable(), expectedToBeSynced = false)
  }

  @Test
  fun `roamable settings should be synced`() = timeoutRunBlockingAndStopBridge {
    testVariousComponentsShouldBeSyncedOrNot(Roamable(), expectedToBeSynced = true)
  }


  @Test
  @TestFor(issues = ["IJPL-162877"])
  fun `don't sync non-roamable files`() = timeoutRunBlockingAndStopBridge {
    val nonRoamable = ExportableNonRoamable()

    nonRoamable.init()
    val generalSettings = GeneralSettings.getInstance().init()
    initSettingsSync(SettingsSyncBridge.InitMode.JustInit)

    val generalSettingsState = GeneralSettings().apply {
      isSaveOnFrameDeactivation = false
    }.toFileState()
    val nonRoamableState = ExportableNonRoamable().apply {
      aState.foo = "bar"
    }.toFileState()

    remoteCommunicator.prepareFileOnServer(SettingsSnapshot(
      MetaInfo(Instant.now(), getLocalApplicationInfo()),
      setOf(generalSettingsState, nonRoamableState),
      null, emptyMap(), emptySet()
    ))

    waitForSettingsToBeApplied(generalSettings) {
      SettingsSyncEvents.getInstance().fireSettingsChanged(SyncSettingsEvent.SyncRequest)
    }
    Assertions.assertFalse(generalSettings.isSaveOnFrameDeactivation)
    Assertions.assertTrue(nonRoamable.aState.foo == "")

    bridge.waitForAllExecuted()

    val state = nonRoamable::class.annotations.find { it is State } as State
    val file = state.storages.first().value

    // we shouldn't drop non-roamable files as they might be used by other IDEs
    val syncCopy = settingsSyncStorage.resolve("options").resolve(file)
    val localCopy = configDir.resolve("options").resolve(file)
    Assertions.assertTrue(syncCopy.exists(), "File should be copied from server")
    Assertions.assertFalse(localCopy.exists(), "File should not be updated (it's not roamamble)")
  }


  private fun CoroutineScope.testVariousComponentsShouldBeSyncedOrNot(component: BaseComponent, expectedToBeSynced: Boolean) {
    component.aState.foo = "bar"
    runBlocking {
      application.componentStore.saveComponent(component)
    }

    initSettingsSync(SettingsSyncBridge.InitMode.JustInit)

    val state = component::class.annotations.find { it is State } as State
    val file = state.storages.first().value

    val fileExists = settingsSyncStorage.resolve("options").resolve(file).exists()
    val assertMessage = "File $file of ${component::class.simpleName} should ${if (!expectedToBeSynced) "not " else ""}exist"
    if (expectedToBeSynced) {
      Assertions.assertTrue(fileExists, assertMessage)
    }
    else {
      Assertions.assertFalse(fileExists, assertMessage)
    }
  }

  @Test
  fun `local and remote changes in different files are both applied`() = timeoutRunBlockingAndStopBridge {
    val generalSettings = GeneralSettings.getInstance().init()
    initSettingsSync(SettingsSyncBridge.InitMode.JustInit)

    // prepare local commit but don't allow it to be pushed
    UISettings.getInstance().initModifyAndSave {
      compactTreeIndents = true
    }
    // at this point there is an unpushed local commit

    // prepare a remote commit and go online
    val fileState = GeneralSettings().apply {
      isSaveOnFrameDeactivation = false
    }.toFileState()
    remoteCommunicator.prepareFileOnServer(SettingsSnapshot(MetaInfo(Instant.now(), getLocalApplicationInfo()),
                                                            setOf(fileState), null, emptyMap(), emptySet()))
    //remoteCommunicator.offline = false

    executeAndWaitUntilPushed {
      waitForSettingsToBeApplied(generalSettings) {
        SettingsSyncEvents.getInstance().fireSettingsChanged(SyncSettingsEvent.SyncRequest)
        // merge will happen here
      }
    }

    assertServerSnapshot {
      fileState {
        GeneralSettings().withState {
          autoSaveFiles = false
        }
      }
      fileState {
        UISettings().withState {
          compactTreeIndents = true
        }
      }
    }
    Assertions.assertFalse(generalSettings.isSaveOnFrameDeactivation)
    Assertions.assertTrue(UISettings.getInstance().compactTreeIndents)
  }

  /*
    @TestFor(issues = ["IDEA-291623"])
    @Test
    fun `zip file size limit exceed`() {
      val notificationServiceSpy = Mockito.spy<NotificationServiceImpl>()
      ApplicationManager.getApplication().replaceService(NotificationService::class.java, notificationServiceSpy, disposable)

      EditorSettingsExternalizable.getInstance().initModifyAndSave {
        languageBreadcrumbsMap = (1..100000).associate { UUID.randomUUID().toString() to true } // please FIXME if you now a better way to make a fat file
      }
      initSettingsSync(SettingsSyncBridge.InitMode.JustInit)

      verify(notificationServiceSpy).notifyZipSizeExceed()
    }*/

  //@Test
  fun `only changed components should be reloaded`() {
    TODO()
  }
}
