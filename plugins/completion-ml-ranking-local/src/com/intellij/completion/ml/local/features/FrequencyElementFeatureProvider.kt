package com.intellij.completion.ml.local.features

import com.intellij.codeInsight.completion.CompletionLocation
import com.intellij.codeInsight.completion.ml.ContextFeatures
import com.intellij.codeInsight.completion.ml.ElementFeatureProvider
import com.intellij.codeInsight.completion.ml.MLFeatureValue
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.completion.ml.local.models.LocalModelsManager
import com.intellij.completion.ml.local.models.frequency.ClassesFrequencyLocalModel
import com.intellij.completion.ml.local.util.LocalModelsUtil
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod

class FrequencyElementFeatureProvider : ElementFeatureProvider {
  override fun getName(): String = "local"

  override fun calculateFeatures(element: LookupElement,
                                 location: CompletionLocation,
                                 contextFeatures: ContextFeatures): MutableMap<String, MLFeatureValue> {
    val features = mutableMapOf<String, MLFeatureValue>()
    val psi = element.psiElement
    val receiverClassName = contextFeatures.getUserData(FrequencyContextFeaturesProvider.RECEIVER_CLASS_NAME_KEY)
    val classFrequencies = contextFeatures.getUserData(FrequencyContextFeaturesProvider.RECEIVER_CLASS_FREQUENCIES_KEY)
    if (psi is PsiMethod && receiverClassName != null && classFrequencies != null) {
      LocalModelsUtil.getClassName(psi.containingClass)?.let { className ->
        if (receiverClassName == className) {
          LocalModelsUtil.getMethodName(psi)?.let { methodName ->
            val frequency = classFrequencies.getMethodFrequency(methodName)
            if (frequency > 0) {
              val totalUsages = classFrequencies.getTotalFrequency()
              features["absolute_method_frequency"] = MLFeatureValue.numerical(frequency)
              features["relative_method_frequency"] = MLFeatureValue.numerical(frequency.toDouble() / totalUsages)
            }
          }
        }
      }
    }
    val classesModel = LocalModelsManager.getInstance(location.project).getModel<ClassesFrequencyLocalModel>()
    if (psi is PsiClass && classesModel != null) {
      LocalModelsUtil.getClassName(psi)?.let { className ->
        classesModel.getClass(className)?.let {
          features["absolute_class_frequency"] = MLFeatureValue.numerical(it)
          features["relative_class_frequency"] = MLFeatureValue.numerical(it.toDouble() / classesModel.totalClassesUsages())
        }
      }
    }
    return features
  }
}