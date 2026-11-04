// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.testFramework.annotations.processors

import com.intellij.gradle.testFramework.GradleTestVersions
import com.intellij.gradle.testFramework.annotations.ArgumentsProcessor
import com.intellij.gradle.testFramework.annotations.BaseGradleVersionSource
import com.intellij.gradle.testFramework.annotations.TargetVersions
import com.intellij.util.containers.orNull
import org.gradle.util.GradleVersion
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

    val gradleVersion = GradleVersion.version(GradleTestVersions.BASE_GRADLE_VERSION)

    return CsvCrossProductArgumentsProcessor.crossProduct(listOf(gradleVersion), annotation.value.toList(), ',', ':')
  }
}
