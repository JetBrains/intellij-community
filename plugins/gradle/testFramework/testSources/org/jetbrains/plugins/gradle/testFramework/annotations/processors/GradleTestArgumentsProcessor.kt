// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework.annotations.processors

import com.intellij.util.containers.orNull
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.testFramework.annotations.ArgumentsProcessor
import org.jetbrains.plugins.gradle.testFramework.annotations.GradleTestSource
import org.jetbrains.plugins.gradle.tooling.annotation.TargetVersions
import org.jetbrains.plugins.gradle.tooling.util.VersionMatcher
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.support.ParameterDeclarations
import java.util.stream.Stream

class GradleTestArgumentsProcessor : ArgumentsProcessor<GradleTestSource> {

  private lateinit var annotation: GradleTestSource

  override fun accept(annotation: GradleTestSource) {
    this.annotation = annotation
  }

  override fun provideArguments(parameters: ParameterDeclarations, context: ExtensionContext): Stream<out Arguments> {
    val targetVersions = context.testMethod.orNull()?.getAnnotation(TargetVersions::class.java)
    val gradleVersions = annotation.value.split(",")
      .map { GradleVersion.version(it.trim()) }
      .filter { VersionMatcher(it).isVersionMatch(targetVersions) }

    return crossProductArguments(gradleVersions, annotation.values.toList())
  }

  companion object {
    internal fun crossProductArguments(gradleVersions: List<GradleVersion>, values: List<String>): Stream<out Arguments> {
      return CsvCrossProductArgumentsProcessor.crossProduct(gradleVersions, values, ',', ':')
    }
  }
}
