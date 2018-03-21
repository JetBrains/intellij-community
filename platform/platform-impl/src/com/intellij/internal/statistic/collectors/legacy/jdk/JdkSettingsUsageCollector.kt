// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.collectors.legacy.jdk

import com.intellij.internal.statistic.UsagesCollector
import com.intellij.internal.statistic.beans.GroupDescriptor
import com.intellij.internal.statistic.beans.UsageDescriptor
import java.lang.management.ManagementFactory
import java.util.function.Function

/**
 * @author Konstantin Bulenkov
 */
@Deprecated("")  // to be removed in 2018.2
class JdkSettingsUsageCollector : UsagesCollector() {
  override fun getUsages(): Set<UsageDescriptor> {
    return ManagementFactory.getRuntimeMXBean().inputArguments
      .filter { accept(it) }
      .map { convertToBucket(it) }
      .filter { !it.isEmpty() }
      .map { UsageDescriptor(it) }
      .toSet()
  }

  val prefixes = listOf("-Xms",
                        "-Xmx",
                        "-XX:SoftRefLRUPolicyMSPerMB",
                        "-XX:ReservedCodeCacheSize")

  fun accept(key: String?): Boolean {
    if (key == null) return false

    prefixes.forEach {
      if (key.startsWith(it)) return true
    }

    return false
  }

  private fun convertToBucket(key: String): String {
    val sizeMb = getMegabytes(key).toLong()
    val converter = Function<Long, String> { it.toString() + "Mb" }

    if (key.startsWith("-Xms"))
      return "-Xmx " + findBucket(sizeMb, converter, 512, 750, 1000, 1024, 1500, 2000, 2048, 3000, 4000, 4096, 6000, 8000)

    if (key.startsWith("-Xmx"))
      return "-Xms " + findBucket(sizeMb, converter, 64, 128, 256, 512)

    if (key.startsWith("-XX:SoftRefLRUPolicyMSPerMB"))
      return "-XX:SoftRefLRUPolicyMSPerMB " + findBucket(sizeMb, converter, 50, 100)

    if (key.startsWith("-XX:ReservedCodeCacheSize"))
      return "-XX:ReservedCodeCacheSize " + findBucket(sizeMb, converter, 240, 300, 400, 500)

    return ""
  }

  private fun getMegabytes(s: String): Int {
    var num = prefixes.firstOrNull { s.startsWith(it) }
      ?.let { s.substring(it.length).toUpperCase().trim() }

    if (num == null) return -1
    if (num.startsWith("=")) num = num.substring(1)

    if (num.last().isDigit()) {
      return try { Integer.parseInt(num) } catch (e: Exception) { -1 }
    }

    try {
      val size = Integer.parseInt(num.substring(0, num.length - 1))
      when(num.last()) {
        'B' -> return size / (1024 * 1024)
        'K' -> return size / 1024
        'M' -> return size
        'G' -> return size * 1024
      }
    } catch (e: Exception) { return -1 }

    return -1
  }

  override fun getGroupId(): GroupDescriptor {
    return GroupDescriptor.create("JVM options")
  }
}