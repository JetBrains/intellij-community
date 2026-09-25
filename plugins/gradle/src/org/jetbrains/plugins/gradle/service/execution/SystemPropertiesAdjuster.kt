// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.execution

import com.intellij.gradle.toolingExtension.util.GradleVersionUtil
import com.intellij.openapi.components.service
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.SystemProperties
import org.gradle.util.GradleVersion
import org.jetbrains.annotations.ApiStatus
import java.util.function.Supplier

/**
 * Use with caution! IDE system properties will be changed for the period of running Gradle long-running operations.
 * This is a workaround to fix leaking unwanted IDE system properties to the Gradle process.
 */
@ApiStatus.Internal
open class SystemPropertiesAdjuster {
  /**
   * Returns the system properties to replace while a Gradle operation runs. A `null` value clears the property.
   *
   * The Tooling API of Gradle older than 7.6 passes all client JVM system properties to the build process, see
   * [Gradle TAPI passes all client JVM system properties to build process](https://github.com/gradle/gradle/issues/17745).
   * Gradle 7.6+ does not need these masks, because [GradleExecutionHelper] calls
   * [org.gradle.tooling.LongRunningOperation.withSystemProperties] for each operation.
   * The masks stay for an unknown [gradleVersion].
   */
  open fun getKeyToMask(projectDir: String, gradleVersion: GradleVersion?): Map<String, String?> {
    val properties = HashMap<String, String?>()
    if (Registry.`is`("gradle.tooling.adjust.user.dir", true)) {
      properties["user.dir"] = projectDir
    }
    // the Tooling API changes it in the IDE process (IDEA-388121)
    properties["library.jansi.path"] = null
    if (gradleVersion == null || GradleVersionUtil.isGradleOlderThan(gradleVersion, "7.6")) {
      properties["java.system.class.loader"] = null
      properties["jna.noclasspath"] = null
      properties["jna.boot.library.path"] = null
      properties["jna.nosys"] = null
      properties["java.nio.file.spi.DefaultFileSystemProvider"] = null
      properties["java.util.concurrent.ForkJoinPool.common.threadFactory"] = null
    }
    return properties
  }

  companion object {
    private val lock = Any()

    // guarded by `lock`
    private val maskedProperties = HashMap<String, MaskedProperty>()

    @JvmStatic
    fun <T> executeAdjusted(projectDir: String, supplier: Supplier<T>): T {
      return executeAdjusted(projectDir = projectDir, gradleVersion = null, supplier = supplier)
    }

    /**
     * Operations may overlap. The original value of a property comes back when the last operation that masks the property ends.
     */
    @JvmStatic
    fun <T> executeAdjusted(projectDir: String, gradleVersion: GradleVersion?, supplier: Supplier<T>): T {
      val keyToMask = service<SystemPropertiesAdjuster>().getKeyToMask(projectDir, gradleVersion)
      mask(keyToMask)
      try {
        return supplier.get()
      }
      finally {
        restore(keyToMask.keys)
      }
    }

    private fun mask(keyToMask: Map<String, String?>) {
      synchronized(lock) {
        for ((key, newValue) in keyToMask) {
          val property = maskedProperties.computeIfAbsent(key) { MaskedProperty(originalValue = System.getProperty(it)) }
          property.operationCount++
          if (property.originalValue != null) {
            SystemProperties.setProperty(key, newValue)
          }
        }
      }
    }

    private fun restore(keys: Collection<String>) {
      synchronized(lock) {
        for (key in keys) {
          val property = maskedProperties[key] ?: continue
          if (--property.operationCount == 0) {
            maskedProperties.remove(key)
            SystemProperties.setProperty(key, property.originalValue)
          }
        }
      }
    }
  }
}

private class MaskedProperty(@JvmField val originalValue: String?) {
  @JvmField
  var operationCount: Int = 0
}
