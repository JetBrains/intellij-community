package com.jetbrains.completion.ml.ranker.cb

import com.intellij.internal.ml.DecisionFunction
import com.intellij.internal.ml.FeaturesInfo
import com.intellij.internal.ml.InconsistentMetadataException
import com.intellij.internal.ml.completion.CompletionRankingModelBase
import com.intellij.internal.ml.completion.RankingModelProvider
import com.intellij.openapi.diagnostic.logger
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.TestOnly

abstract class JarCatBoostCompletionModelProvider(@Nls(capitalization = Nls.Capitalization.Title) private val displayName: String,
                                                  @NonNls private val resourceDirectory: String,
                                                  @NonNls private val modelDirectory: String) : RankingModelProvider {
  private val lazyModel: DecisionFunction by lazy {
    val metadataReader = CatBoostResourcesModelMetadataReader(this::class.java, resourceDirectory, modelDirectory)
    val metadata = FeaturesInfo.buildInfo(metadataReader)
    val model = metadataReader.loadModel()
    return@lazy object : CompletionRankingModelBase(metadata) {
      override fun predict(features: DoubleArray): Double {

        val floatArray = FloatArray(features.size)
        for (i in features.indices) {
          floatArray[i] = features[i].toFloat()
        }

        try {
          return model.predict(floatArray, emptyArray<String>()).get(0, 0)
        } catch (t: Throwable) {
          LOG.error(t)
          return 0.0
        }
      }
    }
  }

  override fun getModel(): DecisionFunction = lazyModel

  override fun getDisplayNameInSettings(): String = displayName

  @TestOnly
  fun assertModelMetadataConsistent() {
    try {
      val decisionFunction = model
      decisionFunction.version()

      val unknownRequiredFeatures = decisionFunction.getUnknownFeatures(decisionFunction.requiredFeatures)
      assert(unknownRequiredFeatures.isEmpty()) { "All required features should be known, but $unknownRequiredFeatures unknown" }

      val featuresOrder = decisionFunction.featuresOrder
      val unknownUsedFeatures = decisionFunction.getUnknownFeatures(featuresOrder.map { it.featureName }.distinct())
      assert(unknownUsedFeatures.isEmpty()) { "All used features should be known, but $unknownUsedFeatures unknown" }

      val features = DoubleArray(featuresOrder.size)
      decisionFunction.predict(features)
    }
    catch (e: InconsistentMetadataException) {
      throw AssertionError("Model metadata inconsistent", e)
    }
  }

  companion object {
    private val LOG = logger<JarCatBoostCompletionModelProvider>()
  }
}