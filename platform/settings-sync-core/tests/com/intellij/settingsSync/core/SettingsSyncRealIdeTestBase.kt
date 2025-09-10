package com.intellij.settingsSync.core

import com.intellij.configurationStore.ApplicationStoreImpl
import com.intellij.configurationStore.StateLoadPolicy
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import com.intellij.openapi.components.impl.stores.IComponentStore
import com.intellij.openapi.extensions.DefaultPluginDescriptor
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtil.createTempDirectory
import com.intellij.settingsSync.core.communicator.SettingsSyncUserData
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.replaceService
import com.intellij.util.xmlb.annotations.Attribute
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import java.lang.reflect.Constructor
import java.nio.file.Path
import java.time.Instant
import java.util.concurrent.CountDownLatch
import kotlin.time.Duration.Companion.seconds

internal abstract class SettingsSyncRealIdeTestBase : SettingsSyncTestBase() {
  protected lateinit var componentStore: TestComponentStore
  val testPluginDescriptor: DefaultPluginDescriptor = DefaultPluginDescriptor("test")

  @BeforeEach
  fun setupComponentStore() {
    componentStore = TestComponentStore(configDir)
    val app = application
    app.replaceService(IComponentStore::class.java, componentStore, disposable)
    app.componentStoreImplChanged()
    //warm up
    app.registerService(ExportableNonRoamable::class.java, ExportableNonRoamable::class.java, testPluginDescriptor, false)
    app.registerService(Roamable::class.java, Roamable::class.java, testPluginDescriptor, false)
    //application.registerService(Roamable::class.java, Roamable::class.java, false)

    app.processAllImplementationClasses { _, _ ->
      // do nothing
    }
  }

  internal data class AState(@Attribute var foo: String = "")

  @State(name = "SettingsSyncTestExportableNonRoamable",
         category = SettingsCategory.TOOLS,
         storages = [Storage("settings-sync-test.exportable-non-roamable.xml", roamingType = RoamingType.DISABLED, exportable = true)])
  internal class ExportableNonRoamable : BaseComponent()

  @State(name = "SettingsSyncTestRoamable",
         storages = [Storage("settings-sync-test.roamable.xml", roamingType = RoamingType.DEFAULT)],
         category = SettingsCategory.UI)
  internal class Roamable : BaseComponent()

  internal open class BaseComponent : PersistentStateComponent<AState> {
    var aState = AState()

    override fun getState() = aState

    override fun loadState(state: AState) {
      this.aState = state
    }
  }

  @AfterEach
  fun resetComponentStatesToDefault() {
    val app = application
    app.unregisterService(ExportableNonRoamable::class.java)
    app.unregisterService(Roamable::class.java)
    componentStore.resetComponents()
    app.componentStoreImplChanged()
  }

  protected fun CoroutineScope.initSettingsSync(initMode: SettingsSyncBridge.InitMode = SettingsSyncBridge.InitMode.JustInit, crossIdeSync: Boolean = false) {
    SettingsSyncSettings.getInstance().syncEnabled = true
    SettingsSyncLocalSettings.getInstance().state.crossIdeSyncEnabled = crossIdeSync
    val ideMediator = SettingsSyncIdeMediatorImpl(componentStore, configDir, enabledCondition = { true })
    val controls = SettingsSyncMain.init(this, disposable, settingsSyncStorage, configDir, ideMediator)
    updateChecker = controls.updateChecker
    bridge = controls.bridge
    bridge.initialize(initMode)
    timeoutRunBlocking(5.seconds) {
      while (!bridge.isInitialized) {
        yield()
      }
    }
  }

  protected suspend fun waitForSettingsToBeApplied(vararg componentsToReinit: PersistentStateComponent<*>, execution: () -> Unit) {
    val cdl = CountDownLatch(1)
    componentStore.reinitLatch = cdl

    execution()
    bridge.waitForAllExecuted()

    assertTrue(cdl.wait(), "Didn't await until new settings are applied")

    val reinitedComponents = componentStore.reinitedComponents
    for (componentToReinit in componentsToReinit) {
      val componentName = componentToReinit.name
      assertTrue(reinitedComponents.contains(componentName), "Reinitialized components don't contain $componentName among those: $reinitedComponents")
    }
  }

  protected suspend fun <T : PersistentStateComponent<*>> init(component: T): T {
    componentStore.initComponent(component = component, serviceDescriptor = null, pluginId = PluginManagerCore.CORE_ID)
    @Suppress("UNCHECKED_CAST")
    val defaultConstructor: Constructor<T> = component::class.java.declaredConstructors.find { it.parameterCount == 0 } as Constructor<T>
    val componentInstance: T = defaultConstructor.newInstance()
    componentStore.componentsAndDefaultStates[component] = componentInstance.state!!
    return component
  }

  protected suspend fun <State, Component : PersistentStateComponent<State>> initModifyAndSave(component: Component, modifier: State.() -> Unit): Component {
    init(component)
    component.state!!.modifier()
    runBlocking {
      componentStore.save()
    }
    return component
  }

  protected fun <State, Component : PersistentStateComponent<State>> Component.withState(stateApplier: State.() -> Unit): Component {
    stateApplier(this.state!!)
    return this
  }

  class TestComponentStore(configDir: Path) : ApplicationStoreImpl(ApplicationManager.getApplication()) {
    override val loadPolicy: StateLoadPolicy
      get() = StateLoadPolicy.LOAD

    val componentsAndDefaultStates = mutableMapOf<PersistentStateComponent<*>, Any>()
    val reinitedComponents = mutableListOf<String>()
    lateinit var reinitLatch: CountDownLatch

    init {
      setPath(configDir)
    }

    override fun reinitComponents(componentNames: Set<String>,
                                  changedStorages: Set<StateStorage>,
                                  notReloadableComponents: Collection<String>) {
      super.reinitComponents(componentNames, changedStorages, notReloadableComponents)

      reinitedComponents.addAll(componentNames)
      if (::reinitLatch.isInitialized) reinitLatch.countDown()
    }

    fun resetComponents() {
      for ((component, defaultState) in componentsAndDefaultStates) {
        @Suppress("UNCHECKED_CAST")
        (component as PersistentStateComponent<Any>).loadState(defaultState)
      }
    }

  }

  companion object {
    @JvmStatic
    @BeforeAll
    fun warmUp() {
      val tempDir = createTempDirectory("gitWarmup-${System.currentTimeMillis()}", "beforeAll")
      val parentDisposable = Disposer.newDisposable()
      val gitSettingsLog = GitSettingsLog(
        tempDir.resolve("storage").toPath(),
        tempDir.resolve("config").toPath(),
        parentDisposable,
        { SettingsSyncUserData("mockId", MOCK_CODE,"empty", "dummy") },
        initialSnapshotProvider = {
          SettingsSnapshot(
            SettingsSnapshot.MetaInfo(Instant.now(), null, true),
            emptySet(), null, emptyMap(), emptySet()
          )

        }
      )
      gitSettingsLog.initialize()
      Disposer.dispose(parentDisposable)
    }
  }
}