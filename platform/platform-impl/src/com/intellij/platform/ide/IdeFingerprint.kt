// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide

import com.dynatrace.hash4j.hashing.Hashing
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.application.impl.ApplicationInfoImpl
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressManager
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
fun ideFingerprint(debugHelperToken: Int = 0): Long {
  val startTime = System.currentTimeMillis()

  val hasher = Hashing.komihash5_0().hashStream()

  val appInfo = ApplicationInfoImpl.getShadowInstance()
  hasher.putLong(appInfo.buildTime.toEpochSecond())
  hasher.putString(appInfo.build.asString())

  // loadedPlugins list is sorted
  val loadedPlugins = PluginManagerCore.loadedPlugins
  hasher.putInt(loadedPlugins.size)
  // Classpath is too huge to calculate its fingerprint. Dev Mode is a preferred way to run IDE from sources.
  if (PluginManagerCore.isRunningFromSources()) {
    hasher.putLong(System.currentTimeMillis())
  }
  else {
    val pluginHasher = PluginHasher(expectedSize = loadedPlugins.size)
    for (plugin in loadedPlugins) {
      // no need to check bundled plugins - handled by taking build time and version into account
      if (!plugin.isBundled) {
        ProgressManager.checkCanceled()
        pluginHasher.addPluginFingerprint(plugin = plugin, hasher = hasher)
      }
    }
  }

  hasher.putInt(debugHelperToken)

  val fingerprint = hasher.asLong

  val durationMs = System.currentTimeMillis() - startTime
  val fingerprintString = java.lang.Long.toUnsignedString(fingerprint, Character.MAX_RADIX)
  Logger.getInstance("com.intellij.platform.ide.IdeFingerprint").info("Calculated dependencies fingerprint in $durationMs ms " +
          "(hash=$fingerprintString, buildTime=${appInfo.buildTime.toEpochSecond()}, appVersion=${appInfo.build.asString()})")
  return fingerprint
}