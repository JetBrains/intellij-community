// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.providers.vswin.utilities.registryUtils.impl

import com.intellij.ide.startup.importSettings.providers.vswin.utilities.registryUtils.IRegistryKey
import com.intellij.ide.startup.importSettings.providers.vswin.utilities.registryUtils.IRegistryRoot
import com.intellij.openapi.diagnostic.logger
import com.intellij.util.system.WindowsRegistry
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.lifetime.throwIfNotAlive

typealias WinRegAction<T> = (WindowsRegistry.Key) -> T

private val logger = logger<RegistryKey>()
class RegistryKey internal constructor(val key: String, private val registryRoot: RegistryRoot): IRegistryKey {
    override fun withSuffix(suffix: String): IRegistryKey {
        return RegistryKey("$key$suffix", registryRoot)
    }

    override fun inChild(child: String): IRegistryKey {
        return RegistryKey("$key\\$child", registryRoot)
    }

    override fun getStringValue(value: String): String? = tryExecuteWithKey(key, value) { root ->
        root.open(key)?.use { it.getString(value) }
    }
    override fun getKeys(): List<String>? = tryExecuteWithKey(key) { root -> root.open(key)?.use { it.subKeys().toList() } }
    override fun getValues(): Map<String, Any>? = tryExecuteWithKey(key) { root -> root.open(key)?.use { it.values() } }

    private fun <T> tryExecuteWithKey(vararg args: String, action: WinRegAction<T?>): T? {
        return registryRoot.executeWithKey {
            try {
                return@executeWithKey action(it)
            }
            catch (t: WindowsRegistry.RegistryException) {
                logger.info("registry arguments: ${args.joinToString()}")
                logger.warn("Failed to work with registry", t)
                return@executeWithKey null
            }
        }
    }
}

/** A registry root that owns an open [WindowsRegistry.Key]. The key closes when the [lifetime] ends. */
open class RegistryRoot(private val key: WindowsRegistry.Key, private val lifetime: Lifetime) : IRegistryRoot {
    companion object {
        /** The `HKEY_CURRENT_USER` hive. */
        fun currentUser(lifetime: Lifetime): RegistryRoot {
            val key = requireNotNull(WindowsRegistry.Key.open(WindowsRegistry.Hive.CURRENT_USER, "")) { "HKEY_CURRENT_USER is not available" }
            return RegistryRoot(key, lifetime)
        }
    }

    init {
      lifetime.onTermination {
          closeRoot()
      }
    }

    fun <T> executeWithKey(action: WinRegAction<T>): T {
        lifetime.throwIfNotAlive()
        return action(key)
    }

    override fun fromKey(key: String): IRegistryKey {
        return RegistryKey(key, this)
    }

    protected open fun closeRoot() {
        key.close()
    }
}
