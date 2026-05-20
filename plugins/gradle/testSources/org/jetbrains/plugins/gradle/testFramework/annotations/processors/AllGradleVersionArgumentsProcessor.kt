// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework.annotations.processors

import com.intellij.util.containers.orNull
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.testFramework.annotations.AllGradleVersionsSource
import org.jetbrains.plugins.gradle.testFramework.annotations.ArgumentsProcessor
import org.jetbrains.plugins.gradle.tooling.VersionMatcherRule
import org.jetbrains.plugins.gradle.tooling.annotation.TargetVersions
import org.jetbrains.plugins.gradle.tooling.util.VersionMatcher
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.support.ParameterDeclarations
import java.util.stream.Stream

class AllGradleVersionArgumentsProcessor : ArgumentsProcessor<AllGradleVersionsSource> {

  private lateinit var annotation: AllGradleVersionsSource

  override fun accept(annotation: AllGradleVersionsSource) {
    this.annotation = annotation
  }

  override fun provideArguments(parameters: ParameterDeclarations, context: ExtensionContext): Stream<out Arguments> {
    val targetVersions = (context.testMethod.orNull() ?: context.testClass.orNull())
      ?.getAnnotation(TargetVersions::class.java)
    val allGradleVersions = VersionMatcherRule.SUPPORTED_GRADLE_VERSIONS
      .map { GradleVersion.version(it) }
      .filter { VersionMatcher(it).isVersionMatch(targetVersions) }

    val gradleVersionsToRunProp = System.getProperty("gradle.versions.to.run")
    val gradleVersionsToRun = when {
      gradleVersionsToRunProp.isNullOrBlank() -> allGradleVersions
      gradleVersionsToRunProp == "FIRST_LAST" -> listOfNotNull(
        allGradleVersions.firstOrNull(),
        allGradleVersions.lastOrNull()
      ).distinct()
      gradleVersionsToRunProp.startsWith("LAST:") -> {
        val last = gradleVersionsToRunProp.removePrefix("LAST:").toInt()
        allGradleVersions.takeLast(last)
      }
      else -> {
        gradleVersionsToRunProp.split(",").map { GradleVersion.version(it) }
      }
    }

    return GradleTestArgumentsProcessor.crossProductArguments(gradleVersionsToRun, annotation.value.toList())
  }
}
