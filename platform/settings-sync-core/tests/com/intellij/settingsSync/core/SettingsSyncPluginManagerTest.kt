package com.intellij.settingsSync.core

import com.intellij.idea.TestFor
import com.intellij.openapi.components.SettingsCategory
import com.intellij.settingsSync.core.config.BUNDLED_PLUGINS_ID
import com.intellij.settingsSync.core.plugins.PluginManagerProxy
import com.intellij.settingsSync.core.plugins.SettingsSyncPluginsState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicReference

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsSyncPluginManagerTest : BasePluginManagerTest() {

  @Test
  fun `test install missing plugins`() {
    pushToIdeAndWait(state {
      quickJump(enabled = false)
      typengo(enabled = true)
      ideaLight(enabled = true, category = SettingsCategory.UI)
    })

    val installedPluginIds = testPluginManager.installer.installedPluginIds
    // NB: quickJump should be skipped because it is disabled
    assertEquals(2, installedPluginIds.size)
    assertTrue(installedPluginIds.containsAll(listOf(typengo.pluginId, ideaLight.pluginId)))
  }

  @Test
  fun `test do not install when plugin sync is disabled`() {
    SettingsSyncSettings.getInstance().setCategoryEnabled(SettingsCategory.PLUGINS, false)
    try {
      pushToIdeAndWait(state {
        quickJump(enabled = false)
        typengo(enabled = true)
        ideaLight(enabled = true, category = SettingsCategory.UI)
      })

      val installedPluginIds = testPluginManager.installer.installedPluginIds
      // IdeaLight is a UI plugin, it doesn't fall under PLUGINS category
      assertEquals(1, installedPluginIds.size)
      assertTrue(installedPluginIds.contains(ideaLight.pluginId))
    }
    finally {
      SettingsSyncSettings.getInstance().setCategoryEnabled(SettingsCategory.PLUGINS, true)
    }
  }

  @Test
  fun `test do not change bundled plugins when bundled plugins sync is disabled`() {
    testPluginManager.addPluginDescriptors(quickJump, git4idea, css)
    pluginManager.updateStateFromIdeOnStart(null)
    testPluginManager.disablePlugin(git4idea.pluginId)
    SettingsSyncSettings.getInstance().setSubcategoryEnabled(SettingsCategory.PLUGINS, BUNDLED_PLUGINS_ID, false)
    PluginManagerProxy.getInstance().addPluginStateChangedListener({ pluginDescriptors, enable ->
                                                                     fail("Shouldn't have set enabled=$enable for ${pluginDescriptors.joinToString()}")
                                                                   }, testRootDisposable)
    try {
      pushToIdeAndWait(state {
        git4idea(enabled = true)
        css(enabled = false)
        quickJump(enabled = true)
      })

      val installedPluginIds = testPluginManager.installer.installedPluginIds
      // nothing is installed/enabled
      assertEquals(0, installedPluginIds.size)

      assertPluginManagerState {
        git4idea(enabled = true)
        css(enabled = false)
        quickJump(enabled = true)
      }
    }
    finally {
      SettingsSyncSettings.getInstance().setCategoryEnabled(SettingsCategory.PLUGINS, true)
    }
  }


  @Test
  fun `test don't update state when plugin sync is disabled`() {
    testPluginManager.addPluginDescriptors(quickJump, git4idea)
    pluginManager.updateStateFromIdeOnStart(null)
    SettingsSyncSettings.getInstance().setCategoryEnabled(SettingsCategory.PLUGINS, false)
    try {
      testPluginManager.disablePlugin(git4idea.pluginId)
      assertPluginManagerState {
        quickJump(enabled = true)
      }

    }
    finally {
      SettingsSyncSettings.getInstance().setCategoryEnabled(SettingsCategory.PLUGINS, true)
    }
  }

  @Test
  fun `test do not install UI plugin when UI category is disabled`() {
    SettingsSyncSettings.getInstance().setCategoryEnabled(SettingsCategory.UI, false)
    try {
      pushToIdeAndWait(state {
        quickJump(enabled = false)
        typengo(enabled = true)
        ideaLight(enabled = true, category = SettingsCategory.UI)
      })

      val installedPluginIds = testPluginManager.installer.installedPluginIds
      // IdeaLight is a UI plugin, it doesn't fall under PLUGINS category
      assertEquals(1, installedPluginIds.size)
      assertTrue(installedPluginIds.contains(typengo.pluginId))
    }
    finally {
      SettingsSyncSettings.getInstance().setCategoryEnabled(SettingsCategory.UI, true)
    }
  }

  @Test
  fun `test disable installed plugin`() {
    testPluginManager.addPluginDescriptors(quickJump)
    pluginManager.updateStateFromIdeOnStart(null)

    assertPluginManagerState {
      quickJump(enabled = true)
    }

    pushToIdeAndWait(state {
      quickJump(enabled = false)
      typengo(enabled = true)
      ideaLight(enabled = true, category = SettingsCategory.UI)
    })

    assertFalse(quickJump.isEnabled)
    assertIdeState {
      quickJump(enabled = false)
      typengo(enabled = true)
      ideaLight(enabled = true, category = SettingsCategory.UI)
    }
  }

  @Test
  fun `test disable two plugins at once`() {
    // install two plugins
    testPluginManager.addPluginDescriptors(quickJump, typengo)

    pushToIdeAndWait(state {
      quickJump(enabled = false)
      typengo(enabled = false)
    })

    assertFalse(quickJump.isEnabled)
    assertFalse(typengo.isEnabled)
  }

  @Test
  fun `test update state from IDE`() {
    testPluginManager.addPluginDescriptors(quickJump, typengo, git4idea)

    pluginManager.updateStateFromIdeOnStart(null)

    assertPluginManagerState {
      quickJump(enabled = true)
      typengo(enabled = true)
    }

    testPluginManager.disablePlugin(git4idea.pluginId)

    assertPluginManagerState {
      quickJump(enabled = true)
      typengo(enabled = true)
      git4idea(enabled = false)
    }

    // here we test concurrency, because PluginEnabledStateListener processes only plugins that were affected
    testPluginManager.disablePlugin(typengo.pluginId)
    testPluginManager.enablePlugin(git4idea.pluginId)

    testScope.runCurrent()
    assertPluginManagerState {
      quickJump(enabled = true)
      typengo(enabled = false)
    }
  }

  @Test
  fun `test do not remove entries about disabled plugins which are not installed`() {
    testPluginManager.addPluginDescriptors(typengo, git4idea)

    val savedState = state {
      quickJump(enabled = false)
      typengo(enabled = true)
      git4idea(enabled = true)
    }

    pluginManager.updateStateFromIdeOnStart(savedState)

    assertPluginManagerState {
      quickJump(enabled = false)
      typengo(enabled = true)
      // git4idea is removed because existing bundled enabled plugin is the default state
    }
  }

  @Test
  fun `test push settings to IDE`() {
    testPluginManager.addPluginDescriptors(quickJump, typengo, git4idea)
    pluginManager.updateStateFromIdeOnStart(null)

    pushToIdeAndWait(state {
      quickJump(enabled = false)
      git4idea(enabled = false)
    })

    assertIdeState {
      quickJump(enabled = false)
      typengo(enabled = true)
      git4idea(enabled = false)
    }

    pushToIdeAndWait(state {
      quickJump(enabled = false)
    })
    // no entry for the bundled git4idea plugin => it is enabled

    assertIdeState {
      quickJump(enabled = false)
      typengo(enabled = true)
      git4idea(enabled = true)
    }
  }

  @Test
  @TestFor(issues = ["IDEA-313300"])
  fun `test update removed from IDE on start`() {
    quickJump.isEnabled = false
    testPluginManager.addPluginDescriptors(typengo, quickJump)

    val savedState = state {
      quickJump(enabled = false)
      typengo(enabled = true)
      ideaLight(enabled = true)
    }

    pluginManager.updateStateFromIdeOnStart(savedState)

    assertPluginManagerState {
      quickJump(enabled = false)
      typengo(enabled = true)
      ideaLight(enabled = false) // ideaLight is disabled because it was removed manually (from disk)
    }
  }

  @Test
  @TestFor(issues = ["IDEA-314934"])
  fun `test don't disable effective essential plugins`() {
    testPluginManager.addPluginDescriptors(javascript, css)
    pluginManager.updateStateFromIdeOnStart(null)

    pushToIdeAndWait(state {
      css(enabled = false)
    })

    assertIdeState {
      css(enabled = true) // IDE state shouldn't have changed (css is an essential plugin)
      javascript(enabled = true) // IDE state shouldn't have changed (css is an essential plugin)
    }
    assertPluginManagerState {
      css(enabled = false) // we shouldn't enable it in PluginManager
    }
  }

  @Test
  @TestFor(issues = ["IDEA-314934"])
  fun `test don't disable essential plugins`() {
    testPluginManager.addPluginDescriptors(javascript)
    pluginManager.updateStateFromIdeOnStart(null)

    pushToIdeAndWait(state {
      javascript(enabled = false)
    })

    assertIdeState {
      javascript(enabled = true) // IDE state shouldn't have changed (css is an essential plugin)
    }
    assertPluginManagerState {
      javascript(enabled = false) // we shouldn't enable it in PluginManager
    }
  }

  @Test
  @TestFor(issues = ["IDEA-303581"])
  fun `test don't fail the sync on plugins' action fail`() {
    testPluginManager.addPluginDescriptors(git4idea, quickJump.withEnabled(false))
    pluginManager.updateStateFromIdeOnStart(null)

    testPluginManager.pluginStateExceptionThrower = {
      if (it == git4idea.pluginId)
        throw RuntimeException("Some arbitrary exception")
    }

    val pushedState = state {
      git4idea(enabled = false)
      quickJump(enabled = true)
      typengo(enabled = true)
    }

    pushToIdeAndWait(pushedState)

    assertIdeState(pushedState)
    assertPluginManagerState(pushedState)
  }

  @Test
  @TestFor(issues = ["IDEA-303581"])
  fun `test don't fail the sync when plugin install fails`() {
    testPluginManager.addPluginDescriptors(git4idea, quickJump.withEnabled(false))
    pluginManager.updateStateFromIdeOnStart(null)

    testPluginManager.pluginStateExceptionThrower = {
      if (it == git4idea.pluginId)
        throw RuntimeException("Some arbitrary exception")
    }

    val pushedState = state {
      git4idea(enabled = false)
      quickJump(enabled = true)
      typengo(enabled = true)
    }

    pushToIdeAndWait(pushedState)

    assertIdeState(pushedState)
    assertPluginManagerState(pushedState)
  }

  @Test
  @TestFor(issues = ["IDEA-303581"])
  fun `turn-off sync of plugin that fails to install`() {
    testPluginManager.addPluginDescriptors(git4idea)
    pluginManager.updateStateFromIdeOnStart(null)

    testPluginManager.installer.installPluginExceptionThrower = {
      if (it == quickJump.pluginId)
        throw RuntimeException("Some arbitrary install exception")
    }

    val pushedState = state {
      git4idea(enabled = false)
      quickJump(enabled = true)
      typengo(enabled = true)
    }

    pushToIdeAndWait(pushedState)

    assertIdeState{
      git4idea(enabled = false)
      typengo(enabled = true)
    }
    assertPluginManagerState(pushedState)
    assertFalse(SettingsSyncSettings.getInstance().isSubcategoryEnabled(SettingsCategory.PLUGINS, quickJump.idString))
  }

  @Test
  @TestFor(issues = ["IDEA-305325"])
  fun `don't disable incompatible existing on start`() {
    testPluginManager.addPluginDescriptors(git4idea, cvsOutdated.withEnabled(false))
    pluginManager.updateStateFromIdeOnStart(state {
      cvsOutdated(enabled = true)
    })

    assertIdeState {
      git4idea(enabled = true)
      cvsOutdated(enabled = false)
    }
    assertPluginManagerState {
      cvsOutdated(enabled = true) // remains the same as it's incompatible
    }
  }

  @Test
  @TestFor(issues = ["IDEA-305325"])
  fun `don't disable incompatible absent on start`() {
    val weirdPlugin = TestPluginDescriptor(
      "org.intellij.weird",
      listOf(TestPluginDependency("com.intellij.ephemeral", isOptional = false)),
      isDynamic = false
    )
    testPluginManager.addPluginDescriptors( git4idea)
    pluginManager.updateStateFromIdeOnStart(state {
      git4idea (enabled = true)
      weirdPlugin (enabled = true)
    })

    assertIdeState {
      git4idea (enabled = true)
    }
    assertPluginManagerState {
      weirdPlugin(enabled = true)
      //git4idea (enabled = true)
    }
  }

  @Test
  @TestFor(issues = ["IDEA-303622"])
  fun `show restart required after install` (){
    restart_required_base(false, false, true)
  }
  @Test
  @TestFor(issues = ["IDEA-303622"])
  fun `show restart required after enable` (){
    restart_required_base(true, false, true)
  }
  @Test
  @TestFor(issues = ["IDEA-303622"])
  fun `show restart required after disable` (){
    restart_required_base(true, true, false)
  }

  @Test
  @TestFor(issues = ["IJPL-157227"])
  fun `don't touch localization plugins state in 242+`() {
    val localization_ja = TestPluginDescriptor(
      "com.intellij.ja",
      listOf(TestPluginDependency("com.intellij.modules.platform", isOptional = false)),
      bundled = true
    )
    val localization_kr = TestPluginDescriptor(
      "com.intellij.kr",
      listOf(TestPluginDependency("com.intellij.modules.platform", isOptional = false)),
      bundled = false
    )
    val localization_zh = TestPluginDescriptor(
      "com.intellij.zh",
      listOf(TestPluginDependency("com.intellij.modules.platform", isOptional = false)),
      bundled = false
    )
    testPluginManager.addPluginDescriptors(localization_ja, localization_kr, localization_zh, git4idea, cvsOutdated.withEnabled(false))
    val pushedState = state {
      localization_ja(enabled = true)
      localization_kr(enabled = true)
      git4idea(enabled = true)
      cvsOutdated(enabled = false)
    }

    pushToIdeAndWait(pushedState)

    assertIdeState {
      git4idea(enabled = true)
      cvsOutdated(enabled = false)
      localization_ja(enabled = true)
      localization_kr(enabled = true)
      localization_zh(enabled = true)
    }
    assertPluginManagerState {
      cvsOutdated(enabled = false) // remains the same as it's incompatible
      localization_ja(enabled = true)
      localization_kr(enabled = true)
    }
  }

  @Test
  @TestFor(issues = ["IJPL-181864", "IJPL-186339"])
  fun `don't touch ultimate plugins if ultimate functionality is disabled`() {
    val ultimate = TestPluginDescriptor(
      "com.intellij.modules.ultimate",
      bundled = true
    )
    val ultimoEins = TestPluginDescriptor(
      "com.intellij.eins",
      listOf(TestPluginDependency("com.intellij.modules.ultimate", isOptional = false)),
      bundled = true
    )

    val ultimoZwoa = TestPluginDescriptor(
      "com.intellij.zwoa",
      listOf(TestPluginDependency("com.intellij.eins", isOptional = false)),
      bundled = true
    )

    val ultimoDrei = TestPluginDescriptor(
      "com.intellij.drei",
      listOf(TestPluginDependency("com.intellij.eins", isOptional = false)),
      bundled = true
    )
    testPluginManager.addPluginDescriptors(ultimoEins.withEnabled(false),
                                           ultimoZwoa.withEnabled(false),
                                           ultimoDrei.withEnabled(false),
                                           ultimate.withEnabled(false),
                                           git4idea, cvsOutdated.withEnabled(false))
    val pushedState = state {
      ultimoDrei(enabled = false)
      git4idea(enabled = true)
      cvsOutdated(enabled = false)
    }

    pushToIdeAndWait(pushedState)

    assertIdeState {
      git4idea(enabled = true)
      cvsOutdated(enabled = false)
      ultimoEins(enabled = false)
      ultimoZwoa(enabled = false)
      ultimoDrei(enabled = false)
      ultimate(enabled = false)
    }
    assertPluginManagerState {
      cvsOutdated(enabled = false) // remains the same as it's incompatible
      ultimoDrei(enabled = false)
    }
  }

  @Test
  @TestFor(issues = ["IJPL-157266"])
  fun `disable syncing of incompatible plugin`(){
    val weirdPlugin = TestPluginDescriptor(
      "org.intellij.weird"
    )
    TestPluginDescriptor.ALL.remove(weirdPlugin.pluginId)
    testPluginManager.addPluginDescriptors(git4idea)
    pluginManager.updateStateFromIdeOnStart(state {
      git4idea (enabled = true) // bundled
    })
    assertPluginManagerState {
      // empty
    }

    pushToIdeAndWait(state {
      git4idea(enabled = true)
      weirdPlugin(enabled = true)
    })

    assertIdeState {
      git4idea (enabled = true)
    }
    assertPluginManagerState {
      weirdPlugin(enabled = true)
    }
    assertFalse(SettingsSyncSettings.getInstance().isSubcategoryEnabled(SettingsCategory.PLUGINS, weirdPlugin.idString))
  }

  private fun restart_required_base(installedBefore: Boolean, enabledBefore: Boolean, enabledInPush: Boolean) = runTest {
    val restartRequiredRef = AtomicReference<RestartReason>()
    SettingsSyncEvents.getInstance().addListener(object : SettingsSyncEventListener {
      override fun restartRequired(reason: RestartReason) {
        restartRequiredRef.set(reason)
      }
    })
    testPluginManager.addPluginDescriptors(quickJump)
    if (installedBefore) {
      testPluginManager.addPluginDescriptors(scala.withEnabled(enabledBefore))
    }

    pushToIdeAndWait(state {
      quickJump(enabled = false)
      scala(enabled = enabledInPush)
    })

    assertIdeState {
      quickJump(enabled = false)
      if (installedBefore) {
        scala(enabled = enabledBefore)
      }
    }
    assertNotNull(restartRequiredRef.get(), "Should have processed")
    if (!installedBefore) {
      assert(restartRequiredRef.get() is RestartForPluginInstall)
    } else if (enabledBefore) {
      assert(restartRequiredRef.get() is RestartForPluginDisable)
    } else {
      assert(restartRequiredRef.get() is RestartForPluginEnable)
    }
  }

  private fun pushToIdeAndWait(newState: SettingsSyncPluginsState) {
    testScope.launch {
      pluginManager.pushChangesToIde(newState)
    }
    testScope.runCurrent()
  }
}