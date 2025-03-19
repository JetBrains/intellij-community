package com.intellij.completion.ml.personalization.impl

import com.intellij.completion.ml.personalization.UserFactorBase
import com.intellij.completion.ml.personalization.UserFactorDescriptions
import com.intellij.completion.ml.personalization.UserFactorReaderBase
import com.intellij.completion.ml.personalization.UserFactorUpdaterBase

/**
 * Since template keys are matched only with [com.intellij.textMatching.PrefixMatchingType.START_WITH],
 * only those sessions are taken into account when computing templates usage ratio.
 */
class TemplatesUsageReader(factor: DailyAggregatedDoubleFactor) : UserFactorReaderBase(factor) {
  fun templatesUsageRatio(): Double? {
    val sums = factor.aggregateSum()
    val totalStartingWithPrefix = sums["totalStartingWithPrefix"]
    val templates = sums["templates"]
    if (totalStartingWithPrefix == null || templates == null || totalStartingWithPrefix < 1.0) return null
    return templates / totalStartingWithPrefix
  }
}

class TemplatesUsageUpdater(factor: MutableDoubleFactor) : UserFactorUpdaterBase(factor) {
  fun fireCompletionFinished(startsWithPrefix: Boolean, isTemplate: Boolean) {
    if (startsWithPrefix) {
      factor.incrementOnToday("totalStartingWithPrefix")
    }
    if (isTemplate) {
      factor.incrementOnToday("templates")
    }
  }
}

class TemplatesRatio : UserFactorBase<TemplatesUsageReader>("templatesUsageRatio", UserFactorDescriptions.TEMPLATES_USAGE) {
  override fun compute(reader: TemplatesUsageReader): String? = reader.templatesUsageRatio()?.toString()
}