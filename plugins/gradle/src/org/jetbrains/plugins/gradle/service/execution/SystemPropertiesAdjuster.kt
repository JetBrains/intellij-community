// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.execution

import com.intellij.openapi.components.service
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.SystemProperties
import org.jetbrains.annotations.ApiStatus
import java.util.function.Supplier

/**
 * Use with caution! IDE system properties will be changed for the period of running Gradle long-running operations.
 * This is a workaround to fix leaking unwanted IDE system properties to the Gradle process.
 */
@ApiStatus.Internal
open class SystemPropertiesAdjuster {

  /**
   * This method masks some system properties while computing the action.
   *
   * This is a workaround for
   * [Gradle TAPI passes all client JVM system properties to build process](https://github.com/gradle/gradle/issues/17745).
   * Gradle 7.6 will pass all TAPI client's system properties to the Gradle daemon.
   */
  open fun getKeyToMask(projectDir: String): Map<String, String?> {
    val properties: MutableMap<String, String?> = HashMap()
    if (Registry.`is`("gradle.tooling.adjust.user.dir", true)) {
      properties["user.dir"] = projectDir
    }
    properties["java.system.class.loader"] = null
    properties["jna.noclasspath"] = null
    properties["jna.boot.library.path"] = null
    properties["jna.nosys"] = null
    properties["java.nio.file.spi.DefaultFileSystemProvider"] = null
    properties["java.util.concurrent.ForkJoinPool.common.threadFactory"] = null
    return properties
  }

  companion object {
    @JvmStatic
    fun <T> executeAdjusted(projectDir: String, supplier: Supplier<T>): T {
      val keyToMask = service<SystemPropertiesAdjuster>().getKeyToMask(projectDir)
      val oldValues: MutableMap<String, String?> = HashMap()
      try {
        keyToMask.forEach { (key: String, newVal: String?) ->
          val oldVal = System.getProperty(key)
          oldValues[key] = oldVal
          if (oldVal != null) {
            SystemProperties.setProperty(key, newVal)
          }
        }
        return supplier.get()
      }
      finally {
        // restore original properties
        oldValues.forEach { (k: String, v: String?) ->
          if (v != null) {
            System.setProperty(k, v)
          }
        }
      }
    }
  }
}