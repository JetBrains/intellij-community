package com.intellij.completion.ml.local.models.frequency

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.ml.CompletionEnvironment
import com.intellij.codeInsight.completion.ml.ContextFeatures
import com.intellij.codeInsight.completion.ml.MLFeatureValue
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.completion.ml.local.models.api.LocalModelFeaturesProvider
import com.intellij.completion.ml.local.models.storage.ClassesFrequencyStorage
import com.intellij.completion.ml.local.models.storage.MethodsFrequencyStorage
import com.intellij.completion.ml.local.util.LocalModelsUtil
import com.intellij.openapi.util.Key
import com.intellij.psi.*

class FrequencyLocalModelFeaturesProvider(private val methodsFrequencyStorage: MethodsFrequencyStorage,
                                          private val classesFrequencyStorage: ClassesFrequencyStorage) : LocalModelFeaturesProvider {
  companion object {
    private val RECEIVER_CLASS_NAME_KEY: Key<String> = Key.create("ml.completion.local.models.receiver.class.name")
    private val RECEIVER_CLASS_FREQUENCIES_KEY: Key<MethodsFrequencies> = Key.create("ml.completion.local.models.receiver.class.frequencies")
  }

  override fun calculateContextFeatures(environment: CompletionEnvironment): Map<String, MLFeatureValue> {
    val features = mutableMapOf<String, MLFeatureValue>()
    if (methodsFrequencyStorage.isValid()) {
      getReceiverClass(environment.parameters)?.let { cls ->
        LocalModelsUtil.getClassName(cls)?.let {
          environment.putUserData(RECEIVER_CLASS_NAME_KEY, it)
          methodsFrequencyStorage.get(it)?.let { frequencies ->
            environment.putUserData(RECEIVER_CLASS_FREQUENCIES_KEY, frequencies)
          }
        }
      }
      features["total_methods"] = MLFeatureValue.numerical(methodsFrequencyStorage.totalMethods)
      features["total_methods_usages"] = MLFeatureValue.numerical(methodsFrequencyStorage.totalMethodsUsages)
    }
    if (classesFrequencyStorage.isValid()) {
      features["total_classes"] = MLFeatureValue.numerical(classesFrequencyStorage.totalClasses)
      features["total_classes_usages"] = MLFeatureValue.numerical(classesFrequencyStorage.totalClassesUsages)
    }
    return features
  }

  override fun calculateElementFeatures(element: LookupElement, contextFeatures: ContextFeatures): Map<String, MLFeatureValue> {
    val features = mutableMapOf<String, MLFeatureValue>()
    val psi = element.psiElement
    val receiverClassName = contextFeatures.getUserData(RECEIVER_CLASS_NAME_KEY)
    val classFrequencies = contextFeatures.getUserData(RECEIVER_CLASS_FREQUENCIES_KEY)
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
    if (psi is PsiClass && classesFrequencyStorage.isValid()) {
      LocalModelsUtil.getClassName(psi)?.let { className ->
        classesFrequencyStorage.get(className)?.let {
          features["absolute_class_frequency"] = MLFeatureValue.numerical(it)
          features["relative_class_frequency"] = MLFeatureValue.numerical(it.toDouble() / classesFrequencyStorage.totalClassesUsages)
        }
      }
    }
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