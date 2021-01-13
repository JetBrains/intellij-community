package com.intellij.completion.ml.local.features

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.ml.CompletionEnvironment
import com.intellij.codeInsight.completion.ml.ContextFeatureProvider
import com.intellij.codeInsight.completion.ml.MLFeatureValue
import com.intellij.completion.ml.local.models.LocalModelsManager
import com.intellij.completion.ml.local.models.frequency.FrequencyLocalModel
import com.intellij.completion.ml.local.models.frequency.MethodsFrequencies
import com.intellij.completion.ml.local.util.LocalModelsUtil
import com.intellij.openapi.util.Key
import com.intellij.psi.*

class FrequencyContextFeaturesProvider : ContextFeatureProvider {
  companion object {
    val RECEIVER_CLASS_NAME_KEY: Key<String> = Key.create("ml.completion.local.models.receiver.class.name")
    val RECEIVER_CLASS_FREQUENCIES_KEY: Key<MethodsFrequencies> = Key.create("ml.completion.local.models.receiver.class.frequencies")
    private const val NAME = "frequency"
  }

  override fun getName(): String = NAME

  override fun calculateFeatures(environment: CompletionEnvironment): MutableMap<String, MLFeatureValue> {
    val features = mutableMapOf<String, MLFeatureValue>()
    val model = LocalModelsManager.getInstance(environment.parameters.position.project).getModel<FrequencyLocalModel>() ?: return features
    getReceiverClass(environment.parameters)?.let { cls ->
      LocalModelsUtil.getClassName(cls)?.let {
        environment.putUserData(RECEIVER_CLASS_NAME_KEY, it)
        model.getMethodsByClass(it)?.let { frequencies ->
          environment.putUserData(RECEIVER_CLASS_FREQUENCIES_KEY, frequencies)
        }
      }
    }
    features["total_methods"] = MLFeatureValue.numerical(model.totalMethodsCount())
    features["total_methods_usages"] = MLFeatureValue.numerical(model.totalMethodsUsages())
    features["total_classes"] = MLFeatureValue.numerical(model.totalClassesCount())
    features["total_classes_usages"] = MLFeatureValue.numerical(model.totalClassesUsages())
    return features
  }

  private fun getReceiverClass(parameters: CompletionParameters): PsiClass? {
    getQualifierExpression(parameters)?.let {
      if (it is PsiCallExpression) {
        return (it.resolveMethod()?.returnType as? PsiClassType)?.resolve()
      }
      return when (val ref = it.reference?.resolve()) {
        is PsiVariable -> (ref.type as? PsiClassType)?.resolve()
        is PsiClass -> ref
        else -> null
      }
    }
    return null
  }

  private fun getQualifierExpression(parameters: CompletionParameters): PsiExpression? {
    return (parameters.position.context as? PsiReferenceExpression)?.qualifierExpression
  }
}