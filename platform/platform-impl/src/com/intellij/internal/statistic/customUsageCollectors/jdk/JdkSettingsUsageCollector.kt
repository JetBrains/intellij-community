/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.internal.statistic.customUsageCollectors.jdk

import com.intellij.internal.statistic.UsagesCollector
import com.intellij.internal.statistic.beans.GroupDescriptor
import com.intellij.internal.statistic.beans.UsageDescriptor
import java.lang.management.ManagementFactory

/**
 * @author Konstantin Bulenkov
 */
class JdkSettingsUsageCollector: UsagesCollector() {
  override fun getUsages(): Set<UsageDescriptor> {
    return ManagementFactory.getRuntimeMXBean().inputArguments
      .map { s -> hideUserPath(s) }
      .map { s -> UsageDescriptor(s) }
      .toSet()
  }

  val keysWithPath = arrayOf("-Didea.home.path", "-Didea.launcher.bin.path", "-Didea.plugins.path", "-Xbootclasspath",
    "-Djb.vmOptionsFile", "-XX ErrorFile", "-XX HeapDumpPath", "	-Didea.launcher.bin.path", "-agentlib:jdwp")

  private fun hideUserPath(key: String): String {
    @Suppress("LoopToCallChain")
    for (s in keysWithPath) {
      if (key.startsWith(s)) return "$s ..."
    }

    return key
  }

  override fun getGroupId(): GroupDescriptor {
    return GroupDescriptor.create("user.jdk.settings")
  }
}