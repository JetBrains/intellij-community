/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.internal.statistic

import com.intellij.internal.statistic.beans.UsageDescriptor

/**
 * Constructs a proper UsageDescriptor for a boolean value,
 * by adding "enabled" or "disabled" suffix to the given key, depending on the value.
 */
fun getBooleanUsage(key: String, value: Boolean): UsageDescriptor {
  return UsageDescriptor(key + if (value) ".enabled" else ".disabled", 1)
}

/**
 * Constructs a proper UsageDescriptor for a counting value.
 * If one needs to know a number of some items in the project, there is no direct way to report usages per-project.
 * Therefore this workaround: create several keys representing interesting ranges, and report that key which correspond to the range
 * which the given value belongs to.
 *
 * For example, to report a number of commits in Git repository, you can call this method like that:
 * ```
 * val usageDescriptor = getCountingUsage("git.commit.count", listOf(0, 1, 100, 10000, 100000), realCommitCount)
 * ```
 * and if there are e.g. 50000 commits in the repository, one usage of the following key will be reported: `git.commit.count.10K+`.
 *
 * @key   The key prefix which will be appended with "." and range code.
 * @steps Limits of the ranges. Each value represents the start of the next range.
 * @value Value to be checked among the given ranges.
 */
fun getCountingUsage(key: String, value: Int, steps: List<Int>) : UsageDescriptor {
  val index = steps.binarySearch(value)
  val stepIndex : Int
  if (index == steps.size) {
    stepIndex = steps.last()
  }
  else if (index >= 0) {
    stepIndex = index
  }
  else {
    stepIndex = -index - 2
  }
  val step = steps[stepIndex]
  val addPlus = stepIndex == steps.size - 1 || steps[stepIndex + 1] != step + 1
  val maybePlus = if (addPlus) "+" else ""
  return UsageDescriptor("$key.${humanize(step)}$maybePlus", 1)
}

private val kilo = 1000
private val mega = kilo * kilo

private fun humanize(number: Int): String {
  if (number == 0) return "0"
  val m = number / mega
  val k = (number % mega) / kilo
  val r = (number % kilo)
  val ms = if (m > 0) "${m}M" else ""
  val ks = if (k > 0) "${k}K" else ""
  val rs = if (r > 0) "${r}" else ""
  return ms + ks + rs
}
