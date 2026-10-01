// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere

import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus
import kotlin.math.min


@Serializable
@ApiStatus.Experimental
class SeWeightComponent(val id: String, val weight: Int) {
  constructor(weight: Int) : this(MATCH, weight)

  companion object {
    const val MATCH: String = "matchWeight"
  }
}

@Serializable
@ApiStatus.Experimental
class SeComposedWeight(val components: List<SeWeightComponent>): Comparable<SeComposedWeight> {
  constructor(weight: Int): this(listOf(SeWeightComponent(weight)))

  init {
    require(components.isNotEmpty())
  }

  override fun toString(): String = "[${components.map { it.weight }.joinToString(", ")}]"

  override fun compareTo(other: SeComposedWeight): Int {
    for (i in 0..<min(components.size, other.components.size)) {
      if (i != 0 && components[i].id != other.components[i].id) return 0
      else if (components[i].weight > other.components[i].weight) return 1
      else if (components[i].weight < other.components[i].weight) return -1
    }

    return 0
  }

  companion object {
    fun from(item: SeItem): SeComposedWeight {
      return when(item) {
        is SeComposedWeightItem -> item.composedWeight()
        else -> SeComposedWeight(item.weight())
      }
    }
  }
}
