package com.intellij.settingsSync.core

import com.intellij.configurationStore.ComponentStoreImpl
import com.intellij.configurationStore.StateStorageManager
import com.intellij.configurationStore.getStateSpec
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.idea.TestFor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import com.intellij.openapi.components.impl.stores.stateStore
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.registerComponentInstance
import com.intellij.testFramework.rules.InMemoryFsRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import org.assertj.core.api.Assertions.assertThat
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.nio.file.Path
import java.time.Instant
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.pathString

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(JUnit4::class)
class SettingsSyncIdeMediatorTest : BasePlatformTestCase() {
  private lateinit var testScope: TestScope

  override fun setUp() {
    super.setUp()
    testScope = TestScope(StandardTestDispatcher(TestCoroutineScheduler()))
  }

  @JvmField @Rule
  val memoryFs = InMemoryFsRule()

  @Test
  fun `process children with subfolders`() {
    val rootConfig = memoryFs.fs.getPath("/appConfig")
    val componentStore = object : ComponentStoreImpl() {
      override val storageManager: StateStorageManager
        get() = TODO("Not yet implemented")

      override fun setPath(path: Path) {
        TODO("Not yet implemented")
      }
    }
    val mediator = SettingsSyncIdeMediatorImpl(componentStore, rootConfig, {true})

    val fileTypes = rootConfig.resolve("filetypes")
    val code = fileTypes.resolve("code").createDirectories()
    code.resolve("myTemplate.kt").createFile()

    val visited = HashSet<String>()
    mediator.processChildren(
      path = fileTypes.pathString,
      roamingType = RoamingType.DEFAULT,
      filter = { true },
      processor = { name, _, _ ->
        visited += name
        true
      },
    )

    assertThat(visited).containsExactlyInAnyOrder("myTemplate.kt")
  }

  @Test
  fun `respect SettingSyncState`() {
    val rootConfig = memoryFs.fs.getPath("/appconfig")
    val componentStore = object : ComponentStoreImpl() {
      override val storageManager: StateStorageManager
        get() = ApplicationManager.getApplication().stateStore.storageManager

      override fun setPath(path: Path) {
        TODO("Not yet implemented")
      }
    }
    val mediator = SettingsSyncIdeMediatorImpl(componentStore, rootConfig, { true })
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
    val syncState = SettingsSyncStateHolder(SettingsSyncSettings.State())
    syncState.syncEnabled = true
    syncState.setCategoryEnabled(SettingsCategory.CODE, false)
    syncState.setSubcategoryEnabled(SettingsCategory.PLUGINS, "IdeaVIM", false)
    testScope.launch {
      mediator.applyToIde(snapshot, syncState)
    }
    testScope.runCurrent()
    Assert.assertTrue(SettingsSyncSettings.getInstance().syncEnabled)
    Assert.assertFalse(SettingsSyncSettings.getInstance().migrationFromOldStorageChecked)
    Assert.assertFalse(SettingsSyncSettings.getInstance().isCategoryEnabled(SettingsCategory.CODE))
    Assert.assertTrue(SettingsSyncSettings.getInstance().isCategoryEnabled(SettingsCategory.UI))
    Assert.assertTrue(SettingsSyncSettings.getInstance().isCategoryEnabled(SettingsCategory.SYSTEM))

    Assert.assertTrue(SettingsSyncSettings.getInstance().isSubcategoryEnabled(SettingsCategory.PLUGINS, "org.vlang"))
    Assert.assertFalse(SettingsSyncSettings.getInstance().isSubcategoryEnabled(SettingsCategory.PLUGINS, "IdeaVIM"))
  }

  @TestFor(issues = ["IDEA-324914"])
  @Test
  fun `process files2apply last`() {
    val componentManager = ApplicationManager.getApplication()
    val rootConfig = memoryFs.fs.getPath("/IDEA-324914/appConfig")
    val componentStore = object : ComponentStoreImpl() {
      override val storageManager: StateStorageManager
        get() = ApplicationManager.getApplication().stateStore.storageManager

      override fun setPath(path: Path) {
        TODO("Not yet implemented")
      }
    }
    val callbackCalls = mutableListOf<String>()
    val firstComponent = FirstComponent({ callbackCalls.add("First") })
    componentManager.registerComponentInstance(FirstComponent::class.java, firstComponent, getTestRootDisposable())
    runBlocking(Dispatchers.Default) {
      componentStore.initComponent(firstComponent, null, PluginManagerCore.CORE_ID)
    }
    componentStore.storageManager.getStateStorage(getStateSpec(FirstComponent::class.java)!!.storages[0]).createSaveSessionProducer()

    val secondComponent = SecondComponent({ callbackCalls.add("Second") })
    componentManager.registerComponentInstance(SecondComponent::class.java, secondComponent, getTestRootDisposable())
    runBlocking(Dispatchers.Default) {
      componentStore.initComponent(secondComponent, null, PluginManagerCore.CORE_ID)
    }
    componentStore.storageManager.getStateStorage(getStateSpec(SecondComponent::class.java)!!.storages[0]).createSaveSessionProducer()

    val mediator = SettingsSyncIdeMediatorImpl(componentStore = componentStore, rootConfig = rootConfig) { true }
    val metaInfo = SettingsSnapshot.MetaInfo(Instant.now(), null)
    val snapshot = SettingsSnapshot(metaInfo, setOf(FileState.Modified("options/first.xml", """
      <application>
  <component name="FirstComponent">
    <option name="strg" value="aaa" />
  </component>
  </application>
    """.trimIndent().toByteArray()), FileState.Modified("options/second.xml", """
      <application>
  <component name="SecondComponent">
    <option name="intt" value="1" />
  </component>
  </application>
    """.trimIndent().toByteArray())), null, emptyMap(), emptySet())
    val syncState = SettingsSyncStateHolder(SettingsSyncSettings.State())
    syncState.syncEnabled = true
    try {
      mediator.activateStreamProvider()
      testScope.launch {
        mediator.applyToIde(snapshot, syncState)
      }
      testScope.runCurrent()
      Assert.assertEquals(2, callbackCalls.size)
      Assert.assertEquals("First", callbackCalls[0])
      Assert.assertEquals("Second", callbackCalls[1])
      callbackCalls.clear()
      mediator.files2applyLast.add("first.xml")
      val newSnapshot = SettingsSnapshot(metaInfo, setOf(FileState.Modified("options/first.xml", """
      <application>
  <component name="FirstComponent">
    <option name="strg" value="bbb" />
  </component>
  </application>
    """.trimIndent().toByteArray()), FileState.Modified("options/second.xml", """
      <application>
  <component name="SecondComponent">
    <option name="intt" value="2" />
  </component>
  </application>
    """.trimIndent().toByteArray())), null, emptyMap(), emptySet())
      testScope.launch {
        mediator.applyToIde(newSnapshot, syncState)
      }
      testScope.runCurrent()
      Assert.assertEquals(2, callbackCalls.size)
      Assert.assertEquals("Second", callbackCalls[0])
      Assert.assertEquals("First", callbackCalls[1])
    } finally {
      mediator.files2applyLast.remove("first.xml")
      mediator.removeStreamProvider()
    }
  }


  @State(
    name = "FirstComponent",
    storages = [Storage("first.xml")],
    category = SettingsCategory.SYSTEM
  )
  internal class FirstComponent(private val loadStateCallback: ()->Unit) : PersistentStateComponent<FirstComponent.FirstState>{

    var internalState = FirstState()
    data class FirstState(
      @JvmField
      var strg: String = ""
    )

    override fun getState(): FirstState {
      return internalState
    }

    override fun loadState(state: FirstState) {
      this.internalState = state
      if (loadStateCallback != null){
        loadStateCallback()
      }
    }
  }

  @State(
    name = "SecondComponent",
    storages = [Storage("second.xml")],
    category = SettingsCategory.SYSTEM
  )
  internal class SecondComponent(private val loadStateCallback: ()->Unit) : PersistentStateComponent<SecondComponent.SecondState>{

    var internalState = SecondState()
    data class SecondState(
      @JvmField
      var intt: Int = 0
    )

    override fun getState(): SecondState {
      return internalState
    }

    override fun loadState(state: SecondState) {
      this.internalState = state
      if (loadStateCallback != null){
        loadStateCallback()
      }
    }
  }
}
