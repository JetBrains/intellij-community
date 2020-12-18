package com.intellij.completion.ml.local.models.frequency

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.ml.CompletionEnvironment
import com.intellij.codeInsight.completion.ml.ContextFeatures
import com.intellij.codeInsight.completion.ml.MLFeatureValue
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.completion.ml.local.models.LocalModel
import com.intellij.completion.ml.local.util.LocalModelsUtil
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.psi.*

class FrequencyLocalModel private constructor(private val project: Project) : LocalModel {
  companion object {
    fun getInstance(project: Project): FrequencyLocalModel = project.getService(FrequencyLocalModel::class.java)

    private const val MODEL_TRAINED_PROPERTY_KEY = "ml.completion.local.models.frequency.trained"
    private val RECEIVER_CLASS_NAME_KEY: Key<String> = Key.create("ml.completion.local.models.receiver.class.name")
    private val RECEIVER_CLASS_FREQUENCIES_KEY: Key<ClassFrequencies> = Key.create("ml.completion.local.models.receiver.class.frequencies")
  }

  private val storage = FrequencyStorage(LocalModelsUtil.storagePath(project))
  private var isTrained = PropertiesComponent.getInstance(project).isTrueValue(MODEL_TRAINED_PROPERTY_KEY)

  init {
    ApplicationManager.getApplication().executeOnPooledThread { storage.postProcess() }
  }

  override fun visitor(): PsiElementVisitor = ReferenceFrequencyVisitor(storage)

  override fun calculateContextFeatures(environment: CompletionEnvironment): Map<String, MLFeatureValue> {
    if (!isTrained) return emptyMap()
    getReceiverClass(environment.parameters)?.let { cls ->
      LocalModelsUtil.getClassName(cls)?.let {
        environment.putUserData(RECEIVER_CLASS_NAME_KEY, it)
        storage.get(it)?.let { frequencies ->
          environment.putUserData(RECEIVER_CLASS_FREQUENCIES_KEY, frequencies)
        }
      }
    }
    return mapOf(
      "total_methods" to MLFeatureValue.Companion.numerical(storage.totalMethods),
      "total_methods_occurrences" to MLFeatureValue.Companion.numerical(storage.totalMethodsOccurrences),
      "total_classes" to MLFeatureValue.Companion.numerical(storage.totalClasses),
      "total_classes_occurrences" to MLFeatureValue.Companion.numerical(storage.totalClassesOccurrences)
    )
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
              val totalOccurrences = classFrequencies.getMethodsFrequency()
              features["absolute_method_frequency"] = MLFeatureValue.Companion.numerical(frequency)
              features["relative_method_frequency"] = MLFeatureValue.Companion.numerical(frequency.toDouble() / totalOccurrences)
            }
          }
        }
      }
    }
    if (psi is PsiClass) {
      LocalModelsUtil.getClassName(psi)?.let { className ->
        storage.getClassFrequency(className)?.let {
          features["absolute_class_frequency"] = MLFeatureValue.Companion.numerical(it)
          features["relative_class_frequency"] = MLFeatureValue.Companion.numerical(it.toDouble() / storage.totalClassesOccurrences)
        }
      }
    }
    return features
  }

  override fun onStarted() {
    storage.clear()
    PropertiesComponent.getInstance(project).setValue(MODEL_TRAINED_PROPERTY_KEY, false)
    isTrained = false
  }

  override fun onFinished() {
    storage.postProcess()
    PropertiesComponent.getInstance(project).setValue(MODEL_TRAINED_PROPERTY_KEY, true)
    isTrained = true
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