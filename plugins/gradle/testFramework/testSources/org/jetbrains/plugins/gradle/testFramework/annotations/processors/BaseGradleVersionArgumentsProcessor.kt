// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework.annotations.processors

import com.intellij.util.containers.orNull
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.testFramework.annotations.ArgumentsProcessor
import org.jetbrains.plugins.gradle.testFramework.annotations.BaseGradleVersionSource
import org.jetbrains.plugins.gradle.tooling.VersionMatcherRule
import org.jetbrains.plugins.gradle.tooling.annotation.TargetVersions
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.support.ParameterDeclarations
import java.util.stream.Stream

class BaseGradleVersionArgumentsProcessor : ArgumentsProcessor<BaseGradleVersionSource> {

  private lateinit var annotation: BaseGradleVersionSource

  override fun accept(annotation: BaseGradleVersionSource) {
    this.annotation = annotation
  }

  override fun provideArguments(parameters: ParameterDeclarations, context: ExtensionContext): Stream<out Arguments> {
    val targetVersions = context.testMethod.orNull()?.getAnnotation(TargetVersions::class.java)
    if (targetVersions != null)
      throw IllegalArgumentException("@BaseGradleVersionSource does not support Gradle version ranges. Use regular assertions instead.")

    val gradleVersion = GradleVersion.version(VersionMatcherRule.BASE_GRADLE_VERSION)

    return CsvCrossProductArgumentsProcessor.crossProduct(listOf(gradleVersion), annotation.value.toList(), ',', ':')
  }
}
