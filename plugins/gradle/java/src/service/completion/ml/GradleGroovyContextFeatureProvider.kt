// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.completion.ml

import com.intellij.codeInsight.completion.ml.CompletionEnvironment
import com.intellij.codeInsight.completion.ml.ContextFeatureProvider
import com.intellij.codeInsight.completion.ml.MLFeatureValue
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.parentOfType
import com.intellij.util.asSafely
import groovy.lang.Closure
import org.jetbrains.plugins.gradle.config.isGradleFile
import org.jetbrains.plugins.gradle.service.resolve.getLinkedGradleProjectPath
import org.jetbrains.plugins.gradle.settings.GradleLocalSettings
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.jetbrains.plugins.groovy.lang.psi.api.GrFunctionalExpression
import org.jetbrains.plugins.groovy.lang.resolve.delegatesTo.getContainingCall
import org.jetbrains.plugins.groovy.lang.resolve.delegatesTo.getDelegatesToInfo

class GradleGroovyContextFeatureProvider : ContextFeatureProvider {
  override fun getName(): String = "gradleGroovy"

  override fun calculateFeatures(environment: CompletionEnvironment): Map<String, MLFeatureValue> {
    val features = mutableMapOf<String, MLFeatureValue>()

    val originalFile = environment.parameters.originalFile
    val position = environment.parameters.originalPosition
    if (!originalFile.isGradleFile()) {
      return emptyMap()
    }

    addIsGradleFeature(features)
    addVersionFeature(originalFile, features)
    addIsSettingsFeature(originalFile, features)
    addLocationFeature(position, features)

    return features
  }

  private fun addIsGradleFeature(features: MutableMap<String, MLFeatureValue>) {
    features["is_gradle_file"] = MLFeatureValue.binary(true)
  }

  private fun addLocationFeature(position: PsiElement?, features: MutableMap<String, MLFeatureValue>) {
    val parentClosure = position?.parentOfType<GrFunctionalExpression>() ?: return
    val delegatesToInfo = getDelegatesToInfo(parentClosure)
    if (delegatesToInfo != null && (delegatesToInfo.strategy == Closure.DELEGATE_FIRST || delegatesToInfo.strategy == Closure.DELEGATE_ONLY)) {
      val type = delegatesToInfo.typeToDelegate?.asSafely<PsiClassType>()?.resolve()?.name
      if (type != null) {
        features["groovy_delegate_$type"] = MLFeatureValue.binary(true)
      }
    }
    val parentMethodName = getContainingCall(parentClosure)?.resolveMethod()?.name
    if (parentMethodName != null) {
      features["groovy_owner_$parentMethodName"] = MLFeatureValue.binary(true)
    }
  }

  private fun addIsSettingsFeature(originalFile: PsiFile,
                        features: MutableMap<String, MLFeatureValue>) {
    val isSettingsGradle = originalFile.name == GradleConstants.SETTINGS_FILE_NAME

    features["is_settings_gradle"] = MLFeatureValue.binary(isSettingsGradle)
  }

  private fun addVersionFeature(originalFile: PsiFile,
                                features: MutableMap<String, MLFeatureValue>) {
    val gradleVersion = originalFile.getLinkedGradleProjectPath()?.let {
      GradleLocalSettings.getInstance(originalFile.project).getGradleVersion(it)
    } ?: "UNKNOWN"

    features["gradle_version"] = MLFeatureValue.version(gradleVersion)
  }

}