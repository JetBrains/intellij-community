// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.updateSettings.impl

import com.intellij.openapi.updateSettings.UpdateStrategyCustomization
import com.intellij.openapi.util.BuildNumber
import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.util.containers.MultiMap
import com.intellij.util.graph.InboundSemiGraph
import com.intellij.util.graph.impl.ShortestPathFinder
import org.jetbrains.annotations.ApiStatus

@IntellijInternalApi
@ApiStatus.Internal
class UpdateStrategy @JvmOverloads constructor(
  private val currentBuild: BuildNumber,
  private val product: Product? = null,
  private val settings: UpdateSettings = UpdateSettings.getInstance(),
  private val customization: UpdateStrategyCustomization = UpdateStrategyCustomization.getInstance(),
) {

  fun checkForUpdates(): PlatformUpdates {
    if (product == null || product.channels.isEmpty()) {
      return PlatformUpdates.Empty
    }

    val selectedChannel = settings.selectedChannelStatus
    val ignoredBuilds = settings.ignoredBuildNumbers.toSet()

    return product.channels
             .asSequence()
             .filter { ch -> customization.isChannelApplicableForUpdates(ch, selectedChannel) }        // filters out inapplicable channels
             .sortedBy { ch -> ch.status }                                                             // reorders channels (EAPs first)
             .flatMap { ch -> ch.builds.asSequence().map { build -> build to ch } }                    // maps into a sequence of <build, channel> pairs
             .filter { p -> isApplicable(p.first, ignoredBuilds) }                                     // filters out inapplicable builds
             .maxWithOrNull(Comparator { p1, p2 -> compareBuilds(p1.first.number, p2.first.number) })  // a build with the max number, preferring the same baseline
             ?.let { (newBuild, channel) ->
               PlatformUpdates.Loaded(
                 newBuild,
                 channel,
                 patches(newBuild, product, currentBuild),
               )
             } ?: PlatformUpdates.Empty
  }

  private fun isApplicable(candidate: BuildInfo, ignoredBuilds: Set<String>): Boolean =
    customization.isNewerVersion(candidate.number, currentBuild) &&
    candidate.number.asStringWithoutProductCode() !in ignoredBuilds &&
    candidate.target?.inRange(currentBuild) ?: true

  private fun compareBuilds(n1: BuildNumber, n2: BuildNumber): Int {
    val preferSameMajorVersion = customization.haveSameMajorVersion(currentBuild, n1).compareTo(customization.haveSameMajorVersion(currentBuild, n2))
    return if (preferSameMajorVersion != 0) preferSameMajorVersion else n1.compareTo(n2)
  }

  private fun patches(newBuild: BuildInfo, product: Product, from: BuildNumber): UpdateChain? {
    val single = newBuild.patches.find { it.isAvailable && it.fromBuild.compareTo(from) == 0 }
    if (single != null) {
      return UpdateChain(listOf(from, newBuild.number), single.size)
    }

    val selectedChannel = settings.selectedChannelStatus
    val upgrades = MultiMap<BuildNumber, BuildNumber>()
    val sizes = mutableMapOf<Pair<BuildNumber, BuildNumber>, Int>()
    val number = Regex("\\d+")

    product.channels
      .filter { ch -> customization.canBeUsedForIntermediatePatches(ch, selectedChannel) }
      .forEach { channel ->
      channel.builds.forEach { build ->
        val toBuild = build.number.withoutProductCode()
        build.patches.forEach { patch ->
          if (patch.isAvailable) {
            val fromBuild = patch.fromBuild.withoutProductCode()
            upgrades.putValue(toBuild, fromBuild)
            if (patch.size != null) {
              val maxSize = number.findAll(patch.size).map { it.value.toIntOrNull() }.filterNotNull().maxOrNull()
              if (maxSize != null) sizes += (fromBuild to toBuild) to maxSize
            }
          }
        }
      }
    }

    val graph = object : InboundSemiGraph<BuildNumber> {
      override fun getNodes() = upgrades.keySet() + upgrades.values()
      override fun getIn(n: BuildNumber) = upgrades[n].iterator()
    }
    val path = ShortestPathFinder(graph).findPath(from.withoutProductCode(), newBuild.number.withoutProductCode())
    if (path == null || path.size <= 2) return null

    var total = 0
    for (i in 1 until path.size) {
      val size = sizes[path[i - 1] to path[i]]
      if (size == null) {
        total = -1
        break
      }
      total += size
    }
    return UpdateChain(path, if (total > 0) total.toString() else null)
  }
}
