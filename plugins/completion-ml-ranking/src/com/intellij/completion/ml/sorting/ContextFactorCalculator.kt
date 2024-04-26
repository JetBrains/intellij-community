@file:Suppress("ReplacePutWithAssignment")

package com.intellij.completion.ml.sorting

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.ml.CompletionEnvironment
import com.intellij.codeInsight.completion.ml.ContextFeatureProvider
import com.intellij.codeInsight.completion.ml.MLFeatureValue
import com.intellij.codeInsight.lookup.Lookup
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.completion.ml.storage.MutableLookupStorage
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.UserDataHolderBase
import java.util.concurrent.TimeUnit
import kotlin.system.measureTimeMillis

object ContextFactorCalculator {
  fun calculateContextFactors(lookup: LookupImpl, parameters: CompletionParameters, storage: MutableLookupStorage) {
    val environment = MyEnvironment(lookup, parameters)
    val contextFeatures = LinkedHashMap<String, MLFeatureValue>()
    for (provider in ContextFeatureProvider.forLanguage(storage.language)) {
      ProgressManager.checkCanceled()
      try {
        val providerName = provider.name
        val timeSpent = measureTimeMillis {
          val features = provider.calculateFeatures(environment)
          for ((featureName, value) in features) {
            contextFeatures.put("ml_ctx_${providerName}_$featureName", value)
          }
        }
        storage.performanceTracker.contextFeaturesCalculated(providerName, TimeUnit.NANOSECONDS.toMillis(timeSpent))
      }
      catch (e: ProcessCanceledException) {
        throw e
      }
      catch (e: Throwable) {
        thisLogger().error(e)
      }
    }

    for (contextFeatureProvider in AdditionalContextFeatureProvider.forLanguage(storage.language)) {
      contextFeatures.putAll(contextFeatureProvider.calculateFeatures(contextFeatures))
    }
    storage.initContextFactors(contextFeatures, environment)
  }
}

private class MyEnvironment(
  private val lookup: LookupImpl,
  private val parameters: CompletionParameters
) : CompletionEnvironment, UserDataHolderBase() {
  override fun getLookup(): Lookup = lookup

  override fun getParameters(): CompletionParameters = parameters
}