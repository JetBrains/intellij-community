package com.intellij.settingsSync.core.migration

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.hints.settings.ParameterNameHintsSettings
import com.intellij.codeInsight.template.impl.TemplateSettings
import com.intellij.configurationStore.getPerOsSettingsStorageFolderName
import com.intellij.ide.GeneralSettings
import com.intellij.ide.projectView.impl.ProjectViewSharedSettings
import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.customization.CustomActionsSchema
import com.intellij.ide.ui.laf.LafManagerImpl
import com.intellij.openapi.components.SettingsCategory
import com.intellij.openapi.editor.colors.impl.EditorColorsManagerImpl
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable
import com.intellij.openapi.keymap.impl.KeymapManagerImpl
import com.intellij.psi.impl.source.codeStyle.PersistableCodeStyleSchemes
import com.intellij.settingsSync.core.SettingsSnapshot
import com.intellij.settingsSync.core.SettingsSyncSettings
import com.intellij.settingsSync.core.assertSettingsSnapshot
import com.intellij.settingsSync.core.migration.CloudConfigToSettingsSyncMigration.Companion.LAYOUT_CONFIG_FILENAME
import com.intellij.settingsSync.core.migration.CloudConfigToSettingsSyncMigration.Companion.LOCAL_LAYOUT_CONFIG_FILENAME
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.rules.InMemoryFsRule
import com.intellij.util.application
import com.intellij.util.io.createDirectories
import com.intellij.util.io.write
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.nio.file.Path
import kotlin.io.path.div

@RunWith(JUnit4::class)
class CloudConfigToSettingsSyncMigrationTest {

  @JvmField @Rule val memoryFs = InMemoryFsRule()
  private val fs get() = memoryFs.fs

  @JvmField @Rule val applicationRule = ApplicationRule()

  private val rootConfig: Path get() = fs.getPath("/AppConfig")

  private val _jbaConfig = lazy {
    (rootConfig / "jba_config").apply {
      (this / "status.info").write("JBA_CONNECTED")
    }
  }

  private val CLASSES: List<Class<*>> = java.util.List.of<Class<*>>(
    KeymapManagerImpl::class.java, EditorColorsManagerImpl::class.java, LafManagerImpl::class.java, GeneralSettings::class.java,
    UISettings::class.java,
    TemplateSettings::class.java, EditorSettingsExternalizable::class.java, CodeInsightSettings::class.java,
    CustomActionsSchema::class.java,
    ParameterNameHintsSettings::class.java, ProjectViewSharedSettings::class.java, PersistableCodeStyleSchemes::class.java
  )

  private val jbaConfig : Path get() = _jbaConfig.value

  private val os get() = CloudConfigToSettingsSyncMigration.calcOS()

  @Test
  fun `test migration from local storage`() {
    (jbaConfig / "laf.xml").write("LaF")
    (jbaConfig / "colors" / "myscheme.icls").write("MyColorScheme")
    (jbaConfig / "$os.keymap.xml").write("Keymap")
    (jbaConfig / "$os.keymaps" / "mykeymap.xml").write("MyKeyMap")
    (jbaConfig / "caches" / "files.7z").write("7z")
    (jbaConfig / "local.changes").write("local changes")
    (jbaConfig / "installed_plugins.txt").write("installed plugins")

    val snapshot = migrate()

    val ros = getPerOsSettingsStorageFolderName()
    snapshot.assertSettingsSnapshot {
      fileState("options/laf.xml", "LaF")
      fileState("colors/myscheme.icls", "MyColorScheme")
      fileState("keymaps/mykeymap.xml", "MyKeyMap")
      fileState("options/$ros/keymap.xml", "Keymap")
    }
  }

  private fun migrate(): SettingsSnapshot {
    val migration = CloudConfigToSettingsSyncMigration()
    assertTrue(migration.isLocalDataAvailable(rootConfig))
    val snapshot = migration.getLocalDataIfAvailable(rootConfig)
    assertNotNull(snapshot)
    return snapshot!!
  }

  @Test fun `ui-lnf-xml is migrated to all-os place`() {
    (jbaConfig / "$os.ui.lnf.xml").write("UISettings")
    val snapshot = migrate()
    snapshot.assertSettingsSnapshot {
      fileState("options/ui.lnf.xml", "UISettings")
    }
  }

  @Test fun `categories sync settings are migrated`() {
    for (clazz in CLASSES) {
      application.getService(clazz)
    }

    (jbaConfig / LAYOUT_CONFIG_FILENAME).write("""
      com.intellij.openapi.editor.colors.impl.EditorColorsManagerImpl:Disable
      com.intellij.codeInsight.hints.settings.ParameterNameHintsSettings:Disable
      """.trimIndent())
    (jbaConfig / LOCAL_LAYOUT_CONFIG_FILENAME).write("com.intellij.ide.ui.laf.LafManagerImpl:DisableLocally")

    val syncSettings = SettingsSyncSettings()
    CloudConfigToSettingsSyncMigration().migrateCategoriesSyncStatus(rootConfig, syncSettings)

    val expectedDisabled = setOf(SettingsCategory.UI, SettingsCategory.CODE)
    for (category in SettingsCategory.values()) {
      if (expectedDisabled.contains(category)) {
        assertFalse("Category '$category' must be disabled", syncSettings.isCategoryEnabled(category))
      }
      else {
        assertTrue("Category '$category' must be enabled", syncSettings.isCategoryEnabled(category))
      }
    }
  }

  @Test fun `editor font sync must be disabled on migration`() {
    jbaConfig.createDirectories()
    val syncSettings = SettingsSyncSettings()
    CloudConfigToSettingsSyncMigration().migrateCategoriesSyncStatus(rootConfig, syncSettings)
    assertFalse("Editor Font sync is enabled but should not", syncSettings.isSubcategoryEnabled(SettingsCategory.UI, "editorFont"))
  }
}