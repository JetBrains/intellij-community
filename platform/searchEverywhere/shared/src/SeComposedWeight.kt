// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere

import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus


@Serializable
@ApiStatus.Internal
class SeWeightKey(val id: String, val order: Int, val defaultWeight: Int) : Comparable<SeWeightKey> {
  init {
    require(if (id == MATCH_ID) order == 0 else order > 0) { "The key '$id' has the order $order" }
  }

  /** The [id] puts two keys with the same [order] in a fixed sequence. */
  override fun compareTo(other: SeWeightKey): Int =
    if (order != other.order) order.compareTo(other.order) else id.compareTo(other.id)

  /** Uses the same fields as [compareTo], so a key from another process is equal to the local one. */
  override fun equals(other: Any?): Boolean =
    this === other || other is SeWeightKey && order == other.order && id == other.id

  override fun hashCode(): Int = 31 * id.hashCode() + order

  override fun toString(): String = id

  companion object {
    private const val MATCH_ID = "matchWeight"

    val MATCH: SeWeightKey = SeWeightKey(MATCH_ID, order = 0, defaultWeight = 0) // The standard weight by name matching
    val RECENCY: SeWeightKey = SeWeightKey("recency", order = 100, defaultWeight = 0) // Recency order for recent files
    val NOT_DEPRECATED: SeWeightKey = SeWeightKey("notDeprecated", order = 200, defaultWeight = 1) // Bonus for being non-deprecated
  }
}

@Serializable
@ApiStatus.Internal
class SeWeightComponent(val key: SeWeightKey, val weight: Int) {
  constructor(weight: Int) : this(SeWeightKey.MATCH, weight)

  val id: String get() = key.id

  override fun toString(): String = "$key=$weight"
}

/**
 * The weight of a Search Everywhere item. It holds one [SeWeightComponent] for each [SeWeightKey].
 *
 * The components go in the order of their keys, and the first component has the key [SeWeightKey.MATCH].
 * Use [of] to build a weight from components in any order.
 */
@Serializable
@ApiStatus.Internal
class SeComposedWeight private constructor(val components: List<SeWeightComponent>): Comparable<SeComposedWeight> {
  constructor(weight: Int): this(listOf(SeWeightComponent(weight)))

  /** The weight of the [SeWeightKey.MATCH] component. */
  val first: Int get() = components.first().weight

  init {
    require(components.isNotEmpty() && components[0].key.order == 0) { "The first component of $this must have the key ${SeWeightKey.MATCH}" }
    for (index in 1..<components.size) {
      require(components[index - 1].key < components[index].key) { "The components of $this are not in the order of their keys" }
    }
  }

  /** Returns a copy of this weight that also holds [component]. A component with the same key is replaced. */
  fun with(component: SeWeightComponent): SeComposedWeight =
    of(components.filter { it.key.compareTo(component.key) != 0 } + component)

  override fun toString(): String = components.joinToString(", ", "[", "]")

  /**
   * Compares the weights key by key, in the order of the keys. A missing component counts as the [SeWeightKey.defaultWeight] of its key.
   *
   * This is a lexicographic order over all keys, so it is transitive for weights from all providers.
   */
  override fun compareTo(other: SeComposedWeight): Int {
    val left = components
    val right = other.components
    var i = 0
    var j = 0

    while (i < left.size || j < right.size) {
      val keyOrder = when {
        i == left.size -> 1
        j == right.size -> -1
        else -> left[i].key.compareTo(right[j].key)
      }

      val result: Int
      if (keyOrder < 0) {
        result = left[i].weight.compareTo(left[i].key.defaultWeight)
        i++
      }
      else if (keyOrder > 0) {
        result = right[j].key.defaultWeight.compareTo(right[j].weight)
        j++
      }
      else {
        result = left[i].weight.compareTo(right[j].weight)
        i++
        j++
      }

      if (result != 0) return result
    }

    return 0
  }

  companion object {
    /** Builds a weight from [components] in any order. */
    fun of(components: List<SeWeightComponent>): SeComposedWeight =
      SeComposedWeight(components.sortedBy { it.key })

    fun from(item: SeItem): SeComposedWeight {
      return when(item) {
        is SeComposedWeightItem -> item.composedWeight
        else -> SeComposedWeight(item.weight())
      }
    }
  }
}
