// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.v2

import com.example.settingsowner.KeyDeclarer
import com.intellij.ide.plugins.cl.PluginAwareClassLoader
import com.intellij.openapi.extensions.DefaultPluginDescriptor
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.extensions.PluginId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import kotlinx.coroutines.CoroutineScope
import java.lang.reflect.InvocationTargetException
import java.nio.file.Path

/**
 * Checks which plugin owns a key. The owner is the plugin whose class declares the key. The key takes it from the
 * loader of that class, so no plugin can declare a key of another plugin.
 *
 * The class also checks that a key knows when its plugin unloads, and that one owner cannot declare one name twice.
 */
class SettingsKeyOwnerTest {
  companion object {
    private val OWNER = PluginId.getId("test.owner.plugin")
  }

  @Test
  fun `a key a plugin class declares belongs to that plugin`() {
    val key = declareAsPlugin("test.owner.detected")

    assertEquals(OWNER, key.pluginId)
    assertEquals("test.owner.plugin.test.owner.detected", key.identity)
  }

  @Test
  fun `a key of a loaded module is not unloading`() {
    val key = declareAsPlugin("test.owner.loaded")

    assertFalse(key.isOriginUnloading)
  }

  @Test
  fun `a key of an unloading module knows it`() {
    val key = declareAsPlugin("test.owner.unloading", OWNER, PluginAwareClassLoader.UNLOAD_IN_PROGRESS)

    assertTrue(key.isOriginUnloading)
  }

  @Test
  fun `a platform key is never unloading`() {
    assertFalse(SettingsKey("test.owner.platformLoaded", default = "d").isOriginUnloading)
  }

  @Test
  fun `a key a platform class declares belongs to no plugin`() {
    val key = SettingsKey("test.owner.platform", default = "d")

    assertNull(key.pluginId)
    assertEquals("test.owner.platform", key.identity)
  }

  @Test
  fun `a key a platform class declares belongs to no plugin when a plugin class loads it`() {
    val key = invokeAsPlugin(FakePluginClassLoader(OWNER), "usePlatformKey")

    assertNull(key.pluginId)
    assertEquals(PlatformKeyDeclarer.NAME, key.identity)
  }

  @Test
  fun `a second key with the same owner and name fails at construction`() {
    SettingsKey("test.owner.duplicate", default = "d")

    assertThrows<IllegalArgumentException> { SettingsKey("test.owner.duplicate", default = "d") }
  }

  @Test
  fun `a key of another owner may take a taken name`() {
    SettingsKey("test.owner.shared", default = "d")

    assertDoesNotThrow { declareAsPlugin("test.owner.shared") }
  }

  @Test
  fun `a reloaded plugin may declare the key of its unloaded copy again`() {
    val unloaded = FakePluginClassLoader(OWNER)
    declareAsPlugin("test.owner.reloaded", unloaded)
    unloaded.loaderState = PluginAwareClassLoader.UNLOAD_IN_PROGRESS

    val key = declareAsPlugin("test.owner.reloaded", FakePluginClassLoader(OWNER))

    assertEquals(OWNER, key.pluginId)
    assertEquals("test.owner.plugin.test.owner.reloaded", key.identity)
  }

  @Test
  fun `two keys are equal when they have the same name and the same owner`() {
    val unloaded = FakePluginClassLoader(OWNER)
    val before = declareAsPlugin("test.owner.equal", unloaded)
    unloaded.loaderState = PluginAwareClassLoader.UNLOAD_IN_PROGRESS
    val after = declareAsPlugin("test.owner.equal", FakePluginClassLoader(OWNER))
    val platformKey = SettingsKey("test.owner.equal", default = "d")

    assertEquals(before, after)
    assertEquals(before.hashCode(), after.hashCode())
    assertNotEquals(before, platformKey)
  }

  @Test
  fun `a second key with the same owner and name from another live loader fails at construction`() {
    declareAsPlugin("test.owner.liveClash")

    assertThrows<IllegalArgumentException> { declareAsPlugin("test.owner.liveClash") }
  }

  @Test
  fun `a second key with the same owner and name from the same plugin loader fails at construction`() {
    val loader = FakePluginClassLoader(OWNER)
    declareAsPlugin("test.owner.sameLoader", loader)

    assertThrows<IllegalArgumentException> { declareAsPlugin("test.owner.sameLoader", loader) }
  }

  private fun declareAsPlugin(name: String): SettingsKey<String> = declareAsPlugin(name, OWNER)
}

/**
 * Declares a key named [name] from [KeyDeclarer], defined again by a class loader that claims [pluginId], so the
 * key under construction sees that plugin as its declaring one.
 *
 * @param loaderState the state that the loader reports. [PluginAwareClassLoader.UNLOAD_IN_PROGRESS] makes an unloading module.
 */
internal fun declareAsPlugin(
  name: String,
  pluginId: PluginId,
  loaderState: Int = PluginAwareClassLoader.ACTIVE,
): SettingsKey<String> = declareAsPlugin(name, FakePluginClassLoader(pluginId, loaderState))

/** Declares a key named [name] from the copy of [KeyDeclarer] that [loader] defines. */
private fun declareAsPlugin(name: String, loader: FakePluginClassLoader): SettingsKey<String> =
  invokeAsPlugin(loader, "declare", name)

/**
 * Calls the [KeyDeclarer] method [methodName] with [arguments], on the copy of [KeyDeclarer] that [loader] defines.
 *
 * @return the key that the method returns
 */
@Suppress("UNCHECKED_CAST")
private fun invokeAsPlugin(loader: FakePluginClassLoader, methodName: String, vararg arguments: String): SettingsKey<String> {
  val declarer = loader.defineAgain(KeyDeclarer::class.java)
  try {
    val instance = declarer.getDeclaredConstructor().newInstance()
    val parameterTypes = Array(arguments.size) { String::class.java }
    return declarer.getMethod(methodName, *parameterTypes).invoke(instance, *arguments) as SettingsKey<String>
  }
  catch (e: InvocationTargetException) {
    throw e.targetException
  }
}

/**
 * Holds a key that a platform class of the settings package declares. The key is built at the first use of this
 * object.
 *
 * Only [KeyDeclarer.usePlatformKey] uses it, so a plugin class always triggers the declaration.
 */
internal object PlatformKeyDeclarer {
  const val NAME: String = "test.owner.platformLoadedByPlugin"

  val KEY: SettingsKey<String> = SettingsKey(NAME, default = "d")
}

/**
 * Claims a plugin and defines a class again from its bytes, so that class belongs to the plugin.
 *
 * @property loaderState the state that the loader reports. A test sets [PluginAwareClassLoader.UNLOAD_IN_PROGRESS] to
 *   unload the module.
 */
private class FakePluginClassLoader(
  private val id: PluginId,
  var loaderState: Int = PluginAwareClassLoader.ACTIVE,
) : ClassLoader(KeyDeclarer::class.java.classLoader), PluginAwareClassLoader {
  /** Returns the copy of [source] that this loader defines, and defines it on the first call. */
  fun defineAgain(source: Class<*>): Class<*> = findLoadedClass(source.name) ?: run {
    val bytes = parent.getResourceAsStream("${source.name.replace('.', '/')}.class")!!.use { it.readBytes() }
    defineClass(source.name, bytes, 0, bytes.size)
  }

  override fun getPluginDescriptor(): PluginDescriptor = DefaultPluginDescriptor(id, null)

  override fun getPluginId(): PluginId = id

  override fun getModuleId(): String? = null
  override fun getEdtTime(): Long = 0
  override fun getBackgroundTime(): Long = 0
  override fun getLoadedClassCount(): Long = 0
  override fun getFiles(): Collection<Path> = emptyList()
  override fun getState(): Int = loaderState
  override fun tryLoadingClass(name: String, forceLoadFromSubPluginClassloader: Boolean): Class<*>? = null
  override fun loadClassInsideSelf(name: String): Class<*>? = null
  override fun getPackagePrefix(): String? = null
  override fun getPluginCoroutineScope(): CoroutineScope = throw UnsupportedOperationException()
}
