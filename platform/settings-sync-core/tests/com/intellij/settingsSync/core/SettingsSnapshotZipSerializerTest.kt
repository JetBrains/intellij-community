package com.intellij.settingsSync.core

import com.intellij.idea.TestFor
import com.intellij.openapi.components.SettingsCategory
import com.intellij.openapi.util.BuildNumber
import com.intellij.openapi.util.io.FileUtil
import com.intellij.settingsSync.core.SettingsSnapshot.AppInfo
import com.intellij.settingsSync.core.SettingsSnapshot.MetaInfo
import com.intellij.testFramework.registerExtension
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.*
import kotlin.random.Random

internal class SettingsSnapshotZipSerializerTest : SettingsSyncTestBase() {

  @BeforeEach
  internal fun initFields() {
    val settingsProvider = SettingsProviderTest.TestSettingsProvider()
    application.registerExtension(SettingsProvider.SETTINGS_PROVIDER_EP, settingsProvider, disposable)
  }

  @Test
  fun `serialize snapshot to zip`() {
    val date = Instant.ofEpochMilli(Random.nextLong())
    val snapshot = settingsSnapshot(
      MetaInfo(date, AppInfo(UUID.randomUUID(), BuildNumber.fromString("IU-231.1"), "FULL_NAME", "john", "home", "/Users/john/ideaconfig/"))) {
      fileState("options/laf.xml", "Laf")
      fileState("colors/my.icls", "Color Scheme")
      fileState("file.xml", "File")
      plugin("com.jetbrains.CyanTheme", false, SettingsCategory.UI, setOf("com.intellij.modules.lang"))
      additionalFile("newformat.json", "New format")
      provided("test", SettingsProviderTest.TestState("just value"))
    }
    val zip = SettingsSnapshotZipSerializer.serializeToZip(snapshot)

    val actualSnapshot = SettingsSnapshotZipSerializer.extractFromZip(zip)
    assertSettingsSnapshotsEqual(snapshot, actualSnapshot!!)
  }

  @Test
  @TestFor(issues = ["IDEA-332017"])
  fun `deserialize from bad zip`() {
    val zipFile = FileUtil.createTempFile(SETTINGS_SYNC_SNAPSHOT_ZIP, null)
    zipFile.writeText("aaaaaaaaa")
    val badSnapshot = SettingsSnapshotZipSerializer.extractFromZip(zipFile.toPath())
    Assertions.assertNull(badSnapshot)
  }
}