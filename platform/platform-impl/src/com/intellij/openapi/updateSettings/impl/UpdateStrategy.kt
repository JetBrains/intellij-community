/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.openapi.updateSettings.impl

import com.intellij.openapi.updateSettings.UpdateStrategyCustomization
import com.intellij.openapi.util.BuildNumber
import java.util.*

class UpdateStrategy(private val currentBuild: BuildNumber, private val updates: UpdatesInfo, private val settings: UserUpdateSettings) {
  enum class State {
    LOADED, CONNECTION_ERROR, NOTHING_LOADED
  }

  private val lineage = currentBuild.baselineVersion

  fun checkForUpdates(): CheckForUpdateResult {
    val product = updates[currentBuild.productCode]
    if (product == null || product.channels.isEmpty()) {
      return CheckForUpdateResult(State.NOTHING_LOADED, null)
    }

    val selectedChannel = settings.selectedChannelStatus
    val ignoredBuilds = settings.ignoredBuildNumbers.toSet()

    val result = product.channels.asSequence()
      .filter { ch -> ch.status >= selectedChannel }                                      // filters out inapplicable channels
      .sortedBy { ch -> ch.status }                                                       // reorders channels (EAPs first)
      .flatMap { ch -> ch.builds.asSequence().map { build -> build to ch } }              // maps into a sequence of <build, channel> pairs
      .filter { p -> isApplicable(p.first, ignoredBuilds) }                               // filters out inapplicable builds
      .maxWith(Comparator { p1, p2 -> compareBuilds(p1.first.number, p2.first.number) })  // a build with the max number, preferring the same baseline

    return CheckForUpdateResult(result?.first, result?.second)
  }

  private fun isApplicable(candidate: BuildInfo, ignoredBuilds: Set<String>) =
      candidate.number > currentBuild &&
      candidate.number.asStringWithoutProductCode() !in ignoredBuilds &&
      candidate.target?.inRange(currentBuild) ?: true

  private fun compareBuilds(n1: BuildNumber, n2: BuildNumber) =
      if (n1.baselineVersion == lineage && n2.baselineVersion != lineage) 1
      else if (n2.baselineVersion == lineage && n1.baselineVersion != lineage) -1
      else n1.compareTo(n2)

  //<editor-fold desc="Deprecated stuff.">

  @Deprecated("use {@link #UpdateStrategy(BuildNumber, UpdatesInfo, UserUpdateSettings)}")
  constructor(@Suppress("UNUSED_PARAMETER") majorVersion: Int,
              @Suppress("UNUSED_PARAMETER") currentBuild: BuildNumber,
              @Suppress("UNUSED_PARAMETER") updatesInfo: UpdatesInfo,
              @Suppress("UNUSED_PARAMETER") updateSettings: UserUpdateSettings) : this(currentBuild, updatesInfo, updateSettings) {
  }


  @Deprecated("use {@link #UpdateStrategy(BuildNumber, UpdatesInfo, UserUpdateSettings)}")
  constructor(@Suppress("UNUSED_PARAMETER") majorVersion: Int,
              @Suppress("UNUSED_PARAMETER") currentBuild: BuildNumber,
              @Suppress("UNUSED_PARAMETER") updatesInfo: UpdatesInfo,
              @Suppress("UNUSED_PARAMETER") updateSettings: UserUpdateSettings,
              @Suppress("UNUSED_PARAMETER") customization: UpdateStrategyCustomization) : this(currentBuild, updatesInfo, updateSettings) {
  }

  //</editor-fold>
}