package com.intellij.settingsSync.core

import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.PluginEnableStateChangedListener
import com.intellij.ide.plugins.PluginEnabler
import com.intellij.openapi.Disposable
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.Disposer
import com.intellij.settingsSync.core.plugins.AbstractPluginManagerProxy
import com.intellij.settingsSync.core.plugins.SettingsSyncPluginInstaller
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert
import java.util.concurrent.CopyOnWriteArrayList

@OptIn(ExperimentalCoroutinesApi::class)
internal class TestPluginManager(val testScope: TestScope) : AbstractPluginManagerProxy() {
  val installer = TestPluginInstaller {
    addPluginDescriptors(TestPluginDescriptor.ALL[it]!!)
  }
  private val ownPluginDescriptors = HashMap<PluginId, IdeaPluginDescriptor>()
  private val pluginEnabledStateListeners = CopyOnWriteArrayList<PluginEnableStateChangedListener>()
  var pluginStateExceptionThrower: ((PluginId) -> Unit)? = null

  override fun getPlugins(): Array<IdeaPluginDescriptor> {
    return ownPluginDescriptors.values.toTypedArray()
  }

  override val pluginEnabler: PluginEnabler
    get() = object : PluginEnabler {
      override fun enableById(pluginIds: MutableSet<PluginId>): Boolean {
        val enabledList = mutableListOf<IdeaPluginDescriptor>()
        for (plugin in pluginIds) {
          val descriptor = findPlugin(plugin)
          assert(descriptor is TestPluginDescriptor)
          if ((descriptor as TestPluginDescriptor).isDynamic) {
            descriptor.isEnabled = true
            pluginStateExceptionThrower?.invoke(plugin)
            enabledList.add(descriptor)
          }
        }
        for (pluginListener in pluginEnabledStateListeners) {
          pluginListener.stateChanged(enabledList, true)
        }
        testScope.runCurrent()
        return enabledList.size == pluginIds.size
      }

      override fun disableById(pluginIds: MutableSet<PluginId>): Boolean {
        val disabledList = mutableListOf<IdeaPluginDescriptor>()
        for (plugin in pluginIds) {
          val descriptor = findPlugin(plugin)
          assert(descriptor is TestPluginDescriptor)
          if ((descriptor as TestPluginDescriptor).isDynamic) {
            descriptor.isEnabled = false
            pluginStateExceptionThrower?.invoke(plugin)
            disabledList.add(descriptor)
          }
        }
        for (pluginListener in pluginEnabledStateListeners) {
          pluginListener.stateChanged(disabledList, false)
        }
        testScope.runCurrent()
        return disabledList.size == pluginIds.size
      }

      override fun isDisabled(pluginId: PluginId): Boolean = throw UnsupportedOperationException()
      override fun enable(descriptors: MutableCollection<out IdeaPluginDescriptor>): Boolean = throw UnsupportedOperationException()
      override fun disable(descriptors: MutableCollection<out IdeaPluginDescriptor>): Boolean = throw UnsupportedOperationException()
    }

  override fun isDescriptorEssential(pluginId: PluginId): Boolean {
    val descriptor = ownPluginDescriptors[pluginId] ?: Assert.fail("Cannot find descriptor for pluginId $pluginId")
    return (descriptor as TestPluginDescriptor).essential
  }

  override fun getDisabledPluginIds(): Set<PluginId> {
    return ownPluginDescriptors.filter { (_, descriptor) -> !descriptor.isEnabled }.keys
  }

  override fun isIncompatible(plugin: IdeaPluginDescriptor): Boolean {
    return !(plugin as TestPluginDescriptor).compatible
  }

  override fun addPluginStateChangedListener(listener: PluginEnableStateChangedListener, parentDisposable: Disposable) {
    pluginEnabledStateListeners.add(listener)
    Disposer.register(parentDisposable, Disposable {
      pluginEnabledStateListeners.remove(listener)
      pluginStateExceptionThrower = null
    })
  }

  override fun findPlugin(pluginId: PluginId): IdeaPluginDescriptor? {
    return ownPluginDescriptors[pluginId]
  }

  override fun createInstaller(notifyErrors: Boolean): SettingsSyncPluginInstaller {
    return installer
  }

  fun addPluginDescriptors(vararg descriptors: IdeaPluginDescriptor) {
    for (descriptor in descriptors) {
      ownPluginDescriptors[descriptor.pluginId] = descriptor
    }
  }

  fun disablePlugin(pluginId: PluginId) {
    disablePlugins(setOf(pluginId))
  }

  fun enablePlugin(pluginId: PluginId) {
    enablePlugins(setOf(pluginId))
  }
}