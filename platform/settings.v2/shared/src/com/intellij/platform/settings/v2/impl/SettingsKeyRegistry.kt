// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.v2.impl

// @spec ../../../../../../../../spec/key-registry.spec.md

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointListener
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.extensions.PluginId
import com.intellij.platform.settings.v2.SettingsKey
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus.Experimental
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.CancellationException
import java.util.concurrent.ConcurrentHashMap

/**
 * Registers the settings keys of one plugin, so the platform knows them before anybody reads them.
 *
 * ```xml
 * <extensions defaultExtensionNs="com.intellij">
 *   <settings.v2.keyPromoter implementation="com.example.MyKeyPromoter"/>
 * </extensions>
 * ```
 *
 * The registry knows a key only after a promoter or a write registers it. An identity that nobody promoted or wrote
 * resolves to nothing. A mutation from another side can carry such an identity, so a promoter makes the key known
 * before that mutation arrives.
 */
@Experimental
interface SettingsKeyPromoter {
  /**
   * Registers every key this plugin declares.
   *
   * @param register takes a key of this plugin, and refuses a key of another one
   */
  fun promote(register: (SettingsKey<*>) -> Unit)

  companion object {
    @Internal
    val EP_NAME: ExtensionPointName<SettingsKeyPromoter> = ExtensionPointName("com.intellij.settings.v2.keyPromoter")
  }
}

/**
 * Holds every registered key by its [identity][SettingsKey.identity], so an identity from the wire resolves to the
 * key behind it: its serializer, its filters, its validator and its locality.
 *
 * The keys arrive from [SettingsKeyPromoter], which the registry calls once per extension, off the start path. A
 * write registers a key that no promoter registered ([confirmForWrite]). A caller that needs the whole set waits with
 * [awaitReady].
 */
@Service
@Experimental
class SettingsKeyRegistry(private val scope: CoroutineScope) {
  /**
   * Holds the registered keys by identity. [promoteAllExcept] replaces the whole map, so a reader never sees a
   * half-built set.
   */
  @Volatile
  private var keys = ConcurrentHashMap<String, SettingsKey<*>>()

  /** Completes when the discovery has run every promoter that was loaded when it started. */
  private val ready = CompletableDeferred<Unit>()

  init {
    startDiscovery()
  }

  /**
   * Registers [key] on behalf of [plugin], unless its identity belongs to another key.
   *
   * A second registration of the same key is a no-op. A different key with the same identity is refused, because
   * the identity is what the wire and the storage address, and only one key can answer it.
   *
   * @param plugin the plugin that registers, which must be the one that declared [key]
   * @return `true` when the registry holds [key] after the call
   */
  @Internal
  fun register(key: SettingsKey<*>, plugin: PluginId?): Boolean = register(key, plugin, keys)

  private fun register(key: SettingsKey<*>, plugin: PluginId?, target: ConcurrentHashMap<String, SettingsKey<*>>): Boolean {
    if (key.pluginId != plugin) {
      LOG.error("Plugin '$plugin' promotes settings key '${key.name}' of plugin '${key.pluginId}'. " +
                "Promote only the keys the plugin declares.")
      return false
    }
    val present = target.putIfAbsent(key.identity, key) ?: return true
    if (present === key) return true
    LOG.error("Settings key identity '${key.identity}' belongs to another key. Rename one of them, or give the " +
              "plugin a prefix of its own.")
    return false
  }

  /** Returns the key registered under [identity], or `null` when no promoter registered one. */
  operator fun get(identity: String): SettingsKey<*>? = keys[identity]

  /** Returns `true` when the registry holds this very [key], and not another key of the same identity. */
  fun holds(key: SettingsKey<*>): Boolean = keys[key.identity] === key

  /** Suspends until every promoter has run. */
  suspend fun awaitReady() {
    ready.await()
  }

  /**
   * Runs every promoter, then releases [awaitReady]. Discovery happens once, when the service is created, and in
   * the background, because a promoter loads the classes that declare its keys.
   *
   * The listener replays the loaded extensions after it starts to listen, so a plugin that loads meanwhile is not
   * missed. A promoter that loads later is promoted, and a promoter that unloads makes the registry promote again.
   */
  private fun startDiscovery() {
    scope.launch {
      val listener = object : ExtensionPointListener<SettingsKeyPromoter> {
        override fun extensionAdded(extension: SettingsKeyPromoter, pluginDescriptor: PluginDescriptor) {
          promote(extension, pluginDescriptor)
        }

        override fun extensionRemoved(extension: SettingsKeyPromoter, pluginDescriptor: PluginDescriptor) {
          promoteAllExcept(extension)
        }
      }
      SettingsKeyPromoter.EP_NAME.point.addExtensionPointListener(scope, true, listener)
      ready.complete(Unit)
    }
  }

  /**
   * Builds the set again from every promoter but [removed], and replaces the old set with it.
   *
   * A promoter can unload while its plugin stays: a plugin unloads a module whose dependency is gone. So the
   * registry drops what [removed] promoted, and keeps what the other promoters of that plugin promote. A key that
   * registered through a write, and no promoter holds, goes too. Its next write registers it again.
   */
  private fun promoteAllExcept(removed: SettingsKeyPromoter) {
    val rebuilt = ConcurrentHashMap<String, SettingsKey<*>>()
    SettingsKeyPromoter.EP_NAME.processWithPluginDescriptor { promoter, descriptor ->
      if (promoter !== removed) promote(promoter, descriptor, rebuilt)
    }
    keys = rebuilt
  }

  /** Registers the keys of one promoter, with [descriptor] as the plugin the keys must belong to. */
  @Internal
  fun promote(promoter: SettingsKeyPromoter, descriptor: PluginDescriptor) {
    promote(promoter, descriptor, keys)
  }

  /**
   * Registers the keys of [promoter] into [target].
   *
   * The keys register only after [promoter] returns, so a promoter that fails leaves no key behind. A promoter fails
   * with a [RuntimeException], or with a [LinkageError] when a class that declares its keys does not load. A failure
   * of one promoter is logged and does not stop the others. A cancellation that [promoter] throws on its own counts
   * as its failure. Only a cancellation of the registry [scope] propagates. Each key then registers on its own: a key
   * that [register] refuses does not stop the others.
   */
  private fun promote(promoter: SettingsKeyPromoter, descriptor: PluginDescriptor, target: ConcurrentHashMap<String, SettingsKey<*>>) {
    val promoted = ArrayList<SettingsKey<*>>()
    try {
      promoter.promote { key -> promoted.add(key) }
    }
    catch (e: RuntimeException) {
      if (e is ControlFlowException || e is CancellationException) {
        if (!scope.isActive) throw e
        // A control flow exception must not be logged with its stack trace, so only the fact is reported.
        LOG.warn("Settings key promoter of '${descriptor.pluginId}' was cancelled. Its keys stay unknown.")
      }
      else {
        logPromoterFailure(descriptor, e)
      }
      return
    }
    catch (e: LinkageError) {
      logPromoterFailure(descriptor, e)
      return
    }
    for (key in promoted) {
      register(key, descriptor.pluginId, target)
    }
  }

  private fun logPromoterFailure(descriptor: PluginDescriptor, e: Throwable) {
    LOG.error("Settings key promoter of '${descriptor.pluginId}' failed. Its keys stay unknown.", e)
  }

  /**
   * Checks that this registry holds [key], and registers it when no promoter did.
   *
   * A write calls it. A key that no promoter registered is a missing promotion, so the registry takes it and
   * reports it once, instead of once per write. A promoter that unloads drops such a key, so its next write reports it
   * again. A key of a module that is gone registers nothing, because holding it would keep that module alive. A key
   * whose identity belongs to another key is refused, and the write must not happen: the value would land where
   * another key answers.
   *
   * @return `true` when the write may proceed
   */
  @Internal
  suspend fun confirmForWrite(key: SettingsKey<*>): Boolean {
    awaitReady()
    val present = keys[key.identity]
    if (present === key) return true
    if (present != null) {
      LOG.error("Settings key identity '${key.identity}' belongs to another key, so this write would land under " +
                "that key. Rename one of them, or give the plugin a prefix of its own.")
      return false
    }
    if (key.isOriginUnloading) {
      LOG.error("Settings key '${key.identity}' belongs to a module of plugin '${key.pluginId}', which is not loaded.")
      return true
    }
    LOG.error("Settings key '${key.identity}' was not promoted. Register it in a settings.v2.keyPromoter of " +
              "plugin '${key.pluginId}'.")
    return register(key, key.pluginId)
  }

  companion object {
    private val LOG = logger<SettingsKeyRegistry>()

    fun getInstance(): SettingsKeyRegistry = service()
  }
}

/** Holds `true` while a test runs with the registry check. See [withRegistryChecks]. */
private var registryChecksInTest = false

/**
 * Holds `true` when a write confirms its key against the registry ([SettingsKeyRegistry.confirmForWrite]).
 *
 * A test declares its keys in its own classes and promotes nothing, so the check would report every write a
 * test makes. It is off in a unit test until [withRegistryChecks] turns it on.
 */
internal val registryChecksEnabled: Boolean
  get() = ApplicationManager.getApplication()?.isUnitTestMode != true || registryChecksInTest

/**
 * Turns the registry check on, runs [block], and restores the previous state.
 *
 * @throws IllegalStateException outside a unit test, where the check is on anyway
 */
@TestOnly
suspend fun <T> withRegistryChecks(block: suspend () -> T): T {
  check(ApplicationManager.getApplication()?.isUnitTestMode == true) { "withRegistryChecks is for a unit test only." }
  val previous = registryChecksInTest
  registryChecksInTest = true
  try {
    return block()
  }
  finally {
    registryChecksInTest = previous
  }
}
