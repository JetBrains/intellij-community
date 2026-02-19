// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.java.groovy.ml

import com.intellij.codeInsight.completion.CompletionLocation
import com.intellij.codeInsight.completion.ml.ContextFeatures
import com.intellij.codeInsight.completion.ml.ElementFeatureProvider
import com.intellij.codeInsight.completion.ml.MLFeatureValue
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.gradle.java.groovy.service.resolve.GradleArtifactHandlerContributor
import com.intellij.gradle.java.groovy.service.resolve.GradleDependencyHandlerContributor
import com.intellij.gradle.java.groovy.service.resolve.GradleExtensionProperty
import com.intellij.gradle.java.groovy.service.resolve.GradleExtensionsContributorUtil
import com.intellij.gradle.java.groovy.service.resolve.GradleGroovyProperty
import com.intellij.gradle.java.groovy.service.resolve.GradleNamedDomainCollectionContributor
import com.intellij.gradle.java.groovy.service.resolve.GradleTaskContainerContributor
import com.intellij.psi.OriginInfoAwareElement

class GradleGroovyElementFeatureProvider : ElementFeatureProvider {
  override fun getName(): String = "gradleGroovy"

  enum class SyntheticGradleMember(val value: String) {
    ARTIFACT_HANDLER(GradleArtifactHandlerContributor.ARTIFACTS_ORIGIN_INFO),
    DEPENDENCY_NOTATION(GradleDependencyHandlerContributor.DEPENDENCY_NOTATION),
    USER_CONTRIBUTED_PROPERTY(GradleGroovyProperty.EXTENSION_PROPERTY),
    PROPERTIES_FILE_PROPERTY(GradleExtensionsContributorUtil.PROPERTIES_FILE_ORIGINAL_INFO),
    NAMED_DOMAIN_DECLARATION(GradleNamedDomainCollectionContributor.NAMED_DOMAIN_DECLARATION),
    GRADLE_CONTRIBUTED_PROPERTY(GradleExtensionProperty.GRADLE_EXTENSION_PROPERTY),
    TASK_METHOD(GradleTaskContainerContributor.GRADLE_TASK_INFO),
  }

  companion object {
    private val syntheticMapping: Map<String, SyntheticGradleMember> = SyntheticGradleMember.values().associateBy { it.value }
  }

  override fun calculateFeatures(element: LookupElement,
                                 location: CompletionLocation,
                                 contextFeatures: ContextFeatures): Map<String, MLFeatureValue> {
    val features = mutableMapOf<String, MLFeatureValue>()
    val psi = element.psiElement
    if (psi is OriginInfoAwareElement) {
      val kind = syntheticMapping[psi.originInfo]
      if (kind != null) {
        features["gradle_extension_kind"] = MLFeatureValue.categorical(kind)
      }
    }
    return features
  }
}