// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.completion.ml.storage

import com.intellij.codeInsight.completion.BaseCompletionParameters
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.ml.ContextFeatures
import com.intellij.codeInsight.completion.ml.MLFeatureValue
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.completion.ml.experiments.ExperimentStatus
import com.intellij.completion.ml.features.ContextFeaturesStorage
import com.intellij.completion.ml.performance.MLCompletionPerformanceTracker
import com.intellij.completion.ml.personalization.UserFactorStorage
import com.intellij.completion.ml.personalization.UserFactorsManager
import com.intellij.completion.ml.personalization.session.LookupSessionFactorsStorage
import com.intellij.completion.ml.sorting.RankingModelWrapper
import com.intellij.completion.ml.sorting.RankingSupport
import com.intellij.completion.ml.util.idString
import com.intellij.lang.Language
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.UserDataHolder
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.util.concurrency.annotations.RequiresReadLock
import org.jetbrains.annotations.TestOnly

/**
 * Mutable [LookupStorage] implementation: the single instance attached to a lookup that accumulates
 * ML state (factors, scores, flags) over the course of one completion session.
 */
class MutableLookupStorage(
  override val startedTimestamp: Long,
  override val language: Language,
  override val model: RankingModelWrapper?,
) : LookupStorage {
  private var _userFactors: Map<String, String>? = null
  private var contextFeaturesStorage: ContextFeatures? = null

  private var mlUsed: Boolean = false
  private var shouldReRank = model != null

  private var _loggingEnabled: Boolean = false

  private val item2storage: MutableMap<String, MutableElementStorage> = mutableMapOf()

  override val userFactors: Map<String, String>
    get() = _userFactors ?: emptyMap()

  override val contextFactors: Map<String, String>
    get() = contextFeaturesStorage?.asMap() ?: emptyMap()

  override val performanceTracker: MLCompletionPerformanceTracker = MLCompletionPerformanceTracker()

  companion object {
    private val LOG = logger<MutableLookupStorage>()
    private val LOOKUP_STORAGE = Key.create<MutableLookupStorage>("completion.ml.lookup.storage")

    @Volatile
    private var alwaysComputeFeaturesInTests = true

    /** Test-only override of whether features are always computed; restored when [parentDisposable] is disposed. */
    @TestOnly
    fun setComputeFeaturesAlways(value: Boolean, parentDisposable: Disposable) {
      val valueBefore = alwaysComputeFeaturesInTests
      alwaysComputeFeaturesInTests = value
      Disposer.register(parentDisposable, Disposable {
        alwaysComputeFeaturesInTests = valueBefore
      })
    }

    /** Returns the storage attached to [lookup], or `null` if it has not been initialized yet. */
    fun getMutableLookupStorage(lookup: LookupImpl): MutableLookupStorage? {
      return lookup.getUserData(LOOKUP_STORAGE)
    }

    /** Returns the existing storage for [lookup], or creates and attaches a new one for [language]. */
    fun initOrGetLookupStorage(lookup: LookupImpl, language: Language): MutableLookupStorage {
      val existed = getMutableLookupStorage(lookup)
      if (existed != null) return existed
      val storage = MutableLookupStorage(System.currentTimeMillis(), language, RankingSupport.getRankingModel(language))
      lookup.putUserData(LOOKUP_STORAGE, storage)
      return storage
    }

    /** Returns the storage for [parameters], falling back to (and caching from) the active lookup when needed. */
    fun getMutableLookupStorage(parameters: BaseCompletionParameters): MutableLookupStorage? {
      var storage = (parameters.process as? UserDataHolder)?.getUserData(LOOKUP_STORAGE)
      if (storage == null && parameters is CompletionParameters) {
        val activeLookup = LookupManager.getActiveLookup(parameters.editor) as? LookupImpl
        if (activeLookup != null) {
          storage = getMutableLookupStorage(activeLookup)
          if (storage != null) {
            LOG.debug("Can't get storage from parameters. Fallback to storage from active lookup")
            saveAsUserData(parameters, storage)
          }
        }
      }
      return storage
    }

    /** Stores [storage] as user data on the completion process behind [parameters] for later retrieval. */
    fun saveAsUserData(parameters: BaseCompletionParameters, storage: MutableLookupStorage) {
      val completionProcess = parameters.process
      if (completionProcess is UserDataHolder) {
        completionProcess.putUserData(LOOKUP_STORAGE, storage)
      }
    }
  }

  override val sessionFactors: LookupSessionFactorsStorage = LookupSessionFactorsStorage(startedTimestamp)

  override fun getItemStorage(id: String): MutableElementStorage =
    item2storage.computeIfAbsent(id) {
      MutableElementStorage()
    }

  override fun mlUsed(): Boolean = mlUsed

  /** Marks that ML scores were used to reorder items and records it in the performance tracker. */
  fun fireReorderedUsingMLScores() {
    mlUsed = true
    performanceTracker.reorderedByML()
  }

  override fun shouldComputeFeatures(): Boolean = shouldReRank() ||
                                                  (ApplicationManager.getApplication().isUnitTestMode && alwaysComputeFeaturesInTests) ||
                                                  (_loggingEnabled && !experimentWithoutComputingFeatures())

  override fun shouldReRank(): Boolean = model != null && shouldReRank

  /** Disables ML reranking for the rest of this session (features may still be computed for logging). */
  fun disableReRanking() {
    shouldReRank = false
  }

  /** Returns `true` once [initContextFactors] has populated the context features. */
  fun isContextFactorsInitialized(): Boolean = contextFeaturesStorage != null

  /** Records the computed [factors] and ML [mlScore] for the given [element]. */
  fun fireElementScored(element: LookupElement, factors: MutableMap<String, Any>, mlScore: Double?) {
    getItemStorage(element.idString()).fireElementScored(factors, mlScore)
  }

  /** Lazily computes application- and project-level user factors for [project] (once per session). */
  @RequiresReadLock
  fun initUserFactors(project: Project) {
    if (_userFactors == null) {
      val userFactorValues = mutableMapOf<String, String>()
      val userFactors = UserFactorsManager.getInstance().getAllFactors()
      val applicationStorage: UserFactorStorage = UserFactorStorage.getInstance()
      val projectStorage: UserFactorStorage = UserFactorStorage.getInstance(project)
      for (factor in userFactors) {
        factor.compute(applicationStorage)?.let { userFactorValues["${factor.id}:App"] = it }
        factor.compute(projectStorage)?.let { userFactorValues["${factor.id}:Project"] = it }
      }
      _userFactors = userFactorValues
    }
  }

  override fun contextProvidersResult(): ContextFeatures = contextFeaturesStorage ?: ContextFeaturesStorage.EMPTY

  /** Initializes the context features from [contextFactors] and [environment]; must be called only once. */
  fun initContextFactors(
    contextFactors: Map<String, MLFeatureValue>,
    environment: UserDataHolderBase,
  ) {
    if (isContextFactorsInitialized()) {
      LOG.error("Context factors should be initialized only once")
      return
    }

    val features = ContextFeaturesStorage(contextFactors)
    environment.copyUserDataTo(features)
    contextFeaturesStorage = features
  }

  private fun experimentWithoutComputingFeatures(): Boolean {
    val experimentInfo = ExperimentStatus.getInstance().forLanguage(language)
    return experimentInfo.inExperiment && !experimentInfo.shouldCalculateFeatures
  }

  /** Enables logging of computed features/weights for this lookup. */
  fun markLoggingEnabled() {
    _loggingEnabled = true
  }
}