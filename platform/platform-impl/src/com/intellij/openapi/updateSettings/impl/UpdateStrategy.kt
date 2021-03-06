// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.updateSettings.impl

import com.intellij.openapi.updateSettings.UpdateStrategyCustomization
import com.intellij.openapi.util.BuildNumber
import com.intellij.util.containers.MultiMap
import com.intellij.util.graph.GraphAlgorithms
import com.intellij.util.graph.InboundSemiGraph
import java.util.*

private val NUMBER = Regex("\\d+")

class UpdateStrategy(private val currentBuild: BuildNumber, private val product: Product?, private val settings: UpdateSettings) {
  constructor(currentBuild: BuildNumber, updates: UpdatesInfo, settings: UpdateSettings) :
    this(currentBuild, updates[currentBuild.productCode], settings)

  private val customization = UpdateStrategyCustomization.getInstance()

  enum class State {
    LOADED, CONNECTION_ERROR, NOTHING_LOADED
  }

  fun checkForUpdates(): CheckForUpdateResult {
    if (product == null || product.channels.isEmpty()) {
      return CheckForUpdateResult(State.NOTHING_LOADED, null)
    }

    val selectedChannel = settings.selectedChannelStatus
    val ignoredBuilds = settings.ignoredBuildNumbers.toSet()

    val result = product.channels.asSequence()
      .filter { ch -> ch.status >= selectedChannel }                                            // filters out inapplicable channels
      .sortedBy { ch -> ch.status }                                                             // reorders channels (EAPs first)
      .flatMap { ch -> ch.builds.asSequence().map { build -> build to ch } }                    // maps into a sequence of <build, channel> pairs
      .filter { p -> isApplicable(p.first, ignoredBuilds) }                                     // filters out inapplicable builds
      .maxWithOrNull(Comparator { p1, p2 -> compareBuilds(p1.first.number, p2.first.number) })  // a build with the max number, preferring the same baseline

    val newBuild = result?.first
    val updatedChannel = result?.second
    val patches = if (newBuild != null) patches(newBuild, product, currentBuild) else null
    return CheckForUpdateResult(newBuild, updatedChannel, patches)
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

    val upgrades = MultiMap<BuildNumber, BuildNumber>()
    val sizes = mutableMapOf<Pair<BuildNumber, BuildNumber>, Int>()

    product.channels.forEach { channel ->
      channel.builds.forEach { build ->
        val toBuild = build.number.withoutProductCode()
        build.patches.forEach { patch ->
          if (patch.isAvailable) {
            val fromBuild = patch.fromBuild.withoutProductCode()
            upgrades.putValue(toBuild, fromBuild)
            if (patch.size != null) {
              val maxSize = NUMBER.findAll(patch.size).map { it.value.toIntOrNull() }.filterNotNull().maxOrNull()
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
    val path = GraphAlgorithms.getInstance().findShortestPath(graph, from.withoutProductCode(), newBuild.number.withoutProductCode())
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
