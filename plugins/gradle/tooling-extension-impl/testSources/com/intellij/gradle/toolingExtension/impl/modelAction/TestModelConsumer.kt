// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.modelAction

import com.intellij.platform.testFramework.assertion.collectionAssertion.CollectionAssertions.assertEqualsOrdered
import org.gradle.tooling.model.BuildModel
import org.gradle.tooling.model.gradle.BasicGradleProject
import org.jetbrains.plugins.gradle.model.ProjectImportModelProvider.GradleModelConsumer

internal class TestModelConsumer : GradleModelConsumer {

  private val buildModels = ArrayList<TestConsumedModel>()
  private val projectModels = ArrayList<TestConsumedModel>()

  fun assertBuildModels(expectedBuildModels: List<TestConsumedModel>) {
    assertEqualsOrdered(expectedBuildModels, buildModels)
  }

  fun assertProjectModels(expectedProjectModels: List<TestConsumedModel>) {
    assertEqualsOrdered(expectedProjectModels, projectModels)
  }

  override fun consumeBuildModel(buildModel: BuildModel, `object`: Any, clazz: Class<*>) {
    buildModels.add(TestConsumedModel(buildModel, `object`, clazz))
  }

  override fun consumeProjectModel(projectModel: BasicGradleProject, `object`: Any, clazz: Class<*>) {
    projectModels.add(TestConsumedModel(projectModel, `object`, clazz))
  }
}

internal data class TestConsumedModel(
  val target: Any,
  val model: Any,
  val modelClass: Class<*>,
)
