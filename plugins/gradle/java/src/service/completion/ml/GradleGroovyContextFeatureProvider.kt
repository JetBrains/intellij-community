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
import org.jetbrains.plugins.gradle.service.resolve.GradleCommonClassNames.*
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
      val psiClass = delegatesToInfo.typeToDelegate?.asSafely<PsiClassType>()?.resolve()
      if (psiClass != null) {
        val feature = MLFeatureValue.categorical(classMapping[psiClass.qualifiedName] ?: KnownGradleClasses.OTHER)
        features["groovy_delegate_type"] = feature
      }
    }
    val parentMethod = getContainingCall(parentClosure)?.resolveMethod()
    if (parentMethod?.containingClass?.qualifiedName?.startsWith("org.gradle") == true) {
      val feature = MLFeatureValue.categorical(methodMapping[parentMethod.name] ?: KnownGradleMethods.OTHER)
      features["groovy_owner_method_name"] = feature
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


  companion object {
    private enum class KnownGradleMethods(val methodName: String) {
      DEPENDENCIES("dependencies"),
      PLUGINS("plugins"),
      REPOSITORIES("repositories"),
      BUILDSCRIPT("buildscript"),
      REGISTER("register"),
      DEPENDENCY_RESOLUTION_MANAGEMENT("dependencyResolutionManagement"),
      TASK("task"),
      CONFIGURE_EACH("configureEach"),
      OTHER("OTHER")
    }

    private val methodMapping: Map<String, KnownGradleMethods>
      = KnownGradleMethods.values().associateBy(KnownGradleMethods::methodName)

    private enum class KnownGradleClasses(val className: String) {
      PROJECT(GRADLE_API_PROJECT),
      CONFIGURATION(GRADLE_API_CONFIGURATION),
      DEPENDENCY_HANDLER(GRADLE_API_DEPENDENCY_HANDLER),
      REPOSITORY_HANDLER(GRADLE_API_REPOSITORY_HANDLER),
      SOURCE_SET(GRADLE_API_SOURCE_SET),
      DISTRIBUTION(GRADLE_API_DISTRIBUTION),
      DISTRIBUTION_CONTAINER(GRADLE_API_DISTRIBUTION_CONTAINER),
      TASK(GRADLE_API_TASK),
      TASK_CONTAINER(GRADLE_API_TASK_CONTAINER),
      DOMAIN_OBJECT_COLLECTION(GRADLE_API_DOMAIN_OBJECT_COLLECTION),
      NAMED_DOMAIN_OBJECT_COLLECTION(GRADLE_API_NAMED_DOMAIN_OBJECT_COLLECTION),
      OTHER("")
    }

    private val classMapping: Map<String, KnownGradleClasses>
      = KnownGradleClasses.values().associateBy(KnownGradleClasses::className)
  }

}