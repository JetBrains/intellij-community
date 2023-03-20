package com.intellij.cce.util

interface Summary {
  fun group(name: String, init: Summary.() -> Unit)
  fun countingGroup(name: String, maxItems: Int, init: Summary.() -> Unit)
  fun inc(key: String)
  fun asSerializable(): Map<String, Any>

  companion object {
    fun create(): Summary = SummaryImpl()
  }
}

private class SummaryImpl : Summary {
  private val groups: MutableMap<String, Summary> = mutableMapOf()
  private val stats = mutableMapOf<String, Int>()
  override fun inc(key: String) {
    require(key !in groups.keys) { "Key '$key' already in used in group stats (= ${stats[key]})" }
    stats.compute(key) { _, value -> value?.inc() ?: 1 }
  }

  override fun asSerializable(): Map<String, Any> {
    val result = linkedMapOf<String, Any>()
    for ((key, value) in stats.entries.sortedBy { it.key }) {
      result[key] = value
    }

    for ((key, stats) in groups.entries.sortedBy { it.key }) {
      result[key] = stats.asSerializable()
    }

    return result
  }

  override fun group(name: String, init: Summary.() -> Unit) {
    registerGroup(name) { SummaryImpl() }.init()
  }

  override fun countingGroup(name: String, maxItems: Int, init: Summary.() -> Unit) {
    val group = registerGroup(name) { LimitedSummary(maxItems) }
    require(group is LimitedSummary) { "Group $name already declared as non-counting" }
    require(group.limit == maxItems) { "Group $name should specify limit only once" }
    group.init()
  }

  private fun registerGroup(name: String, factory: () -> Summary): Summary {
    require(name !in stats.keys) { "Key '$name' already used in trivial stats (= ${stats[name]})" }
    return groups.computeIfAbsent(name) { factory() }
  }
}

private class LimitedSummary(val limit: Int, private val delegate: Summary = SummaryImpl()) : Summary by delegate {
  override fun asSerializable(): Map<String, Any> {
    val defaultNumbers = delegate.asSerializable()
    val result = linkedMapOf<String, Any>()
    val counters = mutableMapOf<String, Int>()
    for ((key, value) in defaultNumbers.entries) {
      if (value is Int) {
        counters[key] = value
      } else {
        result[key] = value
      }
    }

    val total = counters.values.sum()
    result["total"] = total

    if (counters.size > limit) {
      val count = counters.entries.sortedByDescending { it.value }.take(limit).sumOf { it.value }

      result["WARNING"] = "only the most frequent $limit of ${counters.size} " +
                          "(cover ${toPercents(count, total)}% of all usages) are listed below"
    }

    for ((key, count) in counters.entries.sortedByDescending { it.value }.take(limit)) {
      result[key] = "$count (${toPercents(count, total)}%)"
    }

    return result
  }

  private fun toPercents(value: Int, total: Int): String = "%.3f".format(100.0 * value / total)
}