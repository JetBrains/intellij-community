package com.intellij.settingsSync.core

import com.intellij.openapi.components.SettingsCategory
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.time.Instant

class SettingsSnapshotTest {

  @Test
  fun `extract dialog state`() {
    val metaInfo = SettingsSnapshot.MetaInfo(Instant.now(), null)
    val settingsSyncXmlState = FileState.Modified("options/settingsSync.xml", """
<application>
  <component name="SettingsSyncSettings">
    <option name="disabledCategories">
      <list>
        <option value="TOOLS" />
        <option value="SYSTEM" />
      </list>
    </option>
    <option name="disabledSubcategories">
      <map>
        <entry key="UI">
          <value>
            <list>
              <option value="editorFont" />
            </list>
          </value>
        </entry>
        <entry key="PLUGINS">
          <value>
            <list>
              <option value="org.vlang" />
            </list>
          </value>
        </entry>
      </map>
    </option>
    <option name="migrationFromOldStorageChecked" value="true" />
    <option name="syncEnabled" value="true" />
  </component>
</application>      
    """.trimIndent().toByteArray())
    val snapshot = SettingsSnapshot(metaInfo, setOf(settingsSyncXmlState), null, emptyMap(), emptySet())
    val state = snapshot.getState()
    Assertions.assertTrue(state.disabledCategories.containsAll(listOf(SettingsCategory.SYSTEM, SettingsCategory.TOOLS)))
    Assertions.assertEquals(2,state.disabledCategories.size)
    Assertions.assertTrue(state.isCategoryEnabled(SettingsCategory.PLUGINS))
    Assertions.assertTrue(state.isCategoryEnabled(SettingsCategory.UI))
    Assertions.assertFalse(state.isSubcategoryEnabled(SettingsCategory.UI, "editorFont"))
    Assertions.assertTrue(state.disabledSubcategories.keys.containsAll(listOf(SettingsCategory.UI, SettingsCategory.PLUGINS)))
    Assertions.assertTrue(state.syncEnabled)
    Assertions.assertTrue(state.migrationFromOldStorageChecked)
  }

  @Test
  fun `extract dialog state 2`() {
    val metaInfo = SettingsSnapshot.MetaInfo(Instant.now(), null)
    val settingsSyncXmlState = FileState.Modified("options/settingsSync.xml", """
<application>
  <component name="SettingsSyncSettings">
    <option name="disabledSubcategories">
      <map>
        <entry key="PLUGINS">
          <value>
            <list>
              <option value="org.vlang" />
            </list>
          </value>
        </entry>
      </map>
    </option>
  </component>
</application>      
    """.trimIndent().toByteArray())
    val snapshot = SettingsSnapshot(metaInfo, setOf(settingsSyncXmlState), null, emptyMap(), emptySet())
    val state = snapshot.getState()
    Assertions.assertTrue(state.isCategoryEnabled(SettingsCategory.TOOLS))
    Assertions.assertTrue(state.isCategoryEnabled(SettingsCategory.PLUGINS))
    Assertions.assertTrue(state.isCategoryEnabled(SettingsCategory.UI))
    Assertions.assertTrue(state.isSubcategoryEnabled(SettingsCategory.UI, "editorFont"))
    Assertions.assertFalse(state.syncEnabled)
    Assertions.assertFalse(state.migrationFromOldStorageChecked)
  }

}