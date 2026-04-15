// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.modelAction

import com.intellij.gradle.toolingExtension.GradleToolingExtensionProperties.PARALLEL_MODEL_FETCH_PROPERTY_KEY
import com.intellij.gradle.toolingExtension.GradleToolingExtensionProperties.USE_RESILIENT_MODEL_FETCH_SYSTEM_PROPERTY_KEY
import com.intellij.gradle.toolingExtension.impl.modelAction.TestBuildController.TestModelRequest
import com.intellij.gradle.toolingExtension.modelAction.GradleModelController.GradleModelFetchRequest.GradleExecutionMode
import com.intellij.gradle.toolingExtension.modelAction.GradleModelController.GradleModelFetchRequest.GradleModelLevel
import com.intellij.gradle.toolingExtension.modelAction.GradleModelController.GradleModelFetchRequest.GradleTraversalMode
import com.intellij.openapi.Disposable
import com.intellij.openapi.observable.util.setSystemProperty
import com.intellij.testFramework.common.mock.notImplemented
import com.intellij.testFramework.junit5.SystemProperty
import com.intellij.testFramework.junit5.TestDisposable
import org.gradle.tooling.model.gradle.BasicGradleProject
import org.gradle.tooling.model.gradle.GradleBuild
import org.gradle.tooling.model.internal.ImmutableDomainObjectSet
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ValueSource

@ParameterizedClass(name = "[{index}] isResilientSyncEnabled={0}")
@ValueSource(booleans = [true, false])
class GradleModelControllerTest(val isResilientSyncEnabled: Boolean) {

  @BeforeEach
  fun setUp(@TestDisposable testDisposable: Disposable) {
    setSystemProperty(USE_RESILIENT_MODEL_FETCH_SYSTEM_PROPERTY_KEY, isResilientSyncEnabled.toString(), testDisposable)
  }

  @Test
  fun `fetch models uses direct project traversal`() {
    val rootProject = MockGradleProject("root")
    val subProject1 = MockGradleProject("sub-project-1", rootProject)
    val subSubProject1 = MockGradleProject("sub-sub-project-1", subProject1)
    val subProject2 = MockGradleProject("sub-project-2", rootProject)
    val subSubProject2 = MockGradleProject("sub-sub-project-2", subProject2)
    val projectModels = listOf(subSubProject2, subProject2, rootProject, subSubProject1, subProject1)
    val buildModel = MockGradleBuild(rootProject, projectModels)

    val testModels = projectModels.associateWith { TestModel.Impl(it) }
    val buildController = TestBuildController().apply {
      registerModels(TestModel::class.java, testModels)
    }
    val modelConsumer = TestModelConsumer()

    GradleModelControllerImpl(buildController)
      .fetchRequest(listOf(buildModel), TestModel::class.java)
      .traversalMode(GradleTraversalMode.DIRECT)
      .execute(modelConsumer)

    buildController.assertRunActionCounts(emptyList())
    buildController.assertModelRequests(testModels.map {
      TestModelRequest(it.key, TestModel::class.java)
    })

    modelConsumer.assertBuildModels(emptyList())
    modelConsumer.assertProjectModels(testModels.map {
      TestConsumedModel(it.key, it.value, TestModel::class.java)
    })
  }

  @Test
  fun `fetch models uses recursive project traversal`() {
    val rootProject = MockGradleProject("root")
    val subProject1 = MockGradleProject("sub-project-1", rootProject)
    val subSubProject1 = MockGradleProject("sub-sub-project-1", subProject1)
    val subProject2 = MockGradleProject("sub-project-2", rootProject)
    val subSubProject2 = MockGradleProject("sub-sub-project-2", subProject2)
    val projectModels = listOf(subSubProject2, subProject2, rootProject, subSubProject1, subProject1)
    val recursiveProjects = listOf(rootProject, subProject1, subProject2, subSubProject1, subSubProject2)
    val buildModel = MockGradleBuild(rootProject, projectModels)

    val testModels = recursiveProjects.associateWith { TestModel.Impl(it) }
    val buildController = TestBuildController().apply {
      registerModels(TestModel::class.java, testModels)
    }
    val modelConsumer = TestModelConsumer()

    GradleModelControllerImpl(buildController)
      .fetchRequest(listOf(buildModel), TestModel::class.java)
      .traversalMode(GradleTraversalMode.RECURSIVE)
      .execute(modelConsumer)

    buildController.assertRunActionCounts(emptyList())
    buildController.assertModelRequests(testModels.map {
      TestModelRequest(it.key, TestModel::class.java)
    })

    modelConsumer.assertBuildModels(emptyList())
    modelConsumer.assertProjectModels(testModels.map {
      TestConsumedModel(it.key, it.value, TestModel::class.java)
    })
  }

  @Test
  fun `fetch models can traverse projects recursively when build has several roots`() {
    val rootProject1 = MockGradleProject("root-project-1")
    val subProject1 = MockGradleProject("sub-project-1", rootProject1)
    val subSubProject1 = MockGradleProject("sub-sub-project-1", subProject1)
    val rootProject2 = MockGradleProject("root-project-2")
    val subProject2 = MockGradleProject("sub-project-2", rootProject2)
    val subSubProject2 = MockGradleProject("sub-sub-project-2", subProject2)
    val projectModels = listOf(rootProject1, rootProject2, subProject2, subProject1, subSubProject2, subSubProject1)
    val projectModelsRecursive = listOf(rootProject1, subProject1, subSubProject1, rootProject2, subProject2, subSubProject2)
    val buildModel = MockGradleBuild(null, projectModels)

    val testModels = projectModelsRecursive.associateWith { TestModel.Impl(it) }
    val buildController = TestBuildController().apply {
      registerModels(TestModel::class.java, testModels)
    }
    val modelConsumer = TestModelConsumer()

    GradleModelControllerImpl(buildController)
      .fetchRequest(listOf(buildModel), TestModel::class.java)
      .traversalMode(GradleTraversalMode.RECURSIVE)
      .execute(modelConsumer)

    buildController.assertRunActionCounts(emptyList())
    buildController.assertModelRequests(testModels.map {
      TestModelRequest(it.key, TestModel::class.java)
    })

    modelConsumer.assertBuildModels(emptyList())
    modelConsumer.assertProjectModels(testModels.map {
      TestConsumedModel(it.key, it.value, TestModel::class.java)
    })
  }

  @Test
  fun `fetch models can target builds`() {
    val buildModel1 = MockGradleBuild(MockGradleProject("root-1"), emptyList())
    val buildModel2 = MockGradleBuild(MockGradleProject("root-2"), emptyList())
    val buildModels = listOf(buildModel1, buildModel2)

    val buildController = TestBuildController().apply {
      registerModel(buildModel1, String::class.java, "build-model-1")
      registerModel(buildModel2, String::class.java, "build-model-2")
    }
    val modelConsumer = TestModelConsumer()

    GradleModelControllerImpl(buildController)
      .fetchRequest(buildModels, String::class.java)
      .modelLevel(GradleModelLevel.BUILD)
      .execute(modelConsumer)

    buildController.assertRunActionCounts(emptyList())
    buildController.assertModelRequests(buildModels.map {
      TestModelRequest(it, String::class.java)
    })

    modelConsumer.assertProjectModels(emptyList())
    modelConsumer.assertBuildModels(listOf(
      TestConsumedModel(buildModel1, "build-model-1", String::class.java),
      TestConsumedModel(buildModel2, "build-model-2", String::class.java),
    ))
  }

  @Test
  fun `fetch models can run in parallel`() {
    val rootProject = MockGradleProject("root")
    val subProject = MockGradleProject("sub-project", rootProject)
    val projectModels = listOf(rootProject, subProject)
    val buildModel = MockGradleBuild(rootProject, projectModels)

    val testModels = projectModels.associateWith { TestModel.Impl(it) }
    val buildController = TestBuildController().apply {
      registerModels(TestModel::class.java, testModels)
    }
    val modelConsumer = TestModelConsumer()

    GradleModelControllerImpl(buildController)
      .fetchRequest(listOf(buildModel), TestModel::class.java)
      .executionMode(GradleExecutionMode.PARALLEL)
      .execute(modelConsumer)

    buildController.assertRunActionCounts(listOf(projectModels.size))
    buildController.assertModelRequests(testModels.map {
      TestModelRequest(it.key, TestModel::class.java)
    })

    modelConsumer.assertBuildModels(emptyList())
    modelConsumer.assertProjectModels(testModels.map {
      TestConsumedModel(it.key, it.value, TestModel::class.java)
    })
  }

  @Test
  @SystemProperty(PARALLEL_MODEL_FETCH_PROPERTY_KEY, true.toString())
  fun `fetch models default execution mode can resolve to parallel`() {
    val rootProject = MockGradleProject("root")
    val projectModels = listOf(rootProject)
    val buildModel = MockGradleBuild(rootProject, projectModels)

    val testModels = projectModels.associateWith { TestModel.Impl(it) }
    val buildController = TestBuildController().apply {
      registerModels(TestModel::class.java, testModels)
    }
    val modelConsumer = TestModelConsumer()

    GradleModelControllerImpl(buildController)
      .fetchRequest(listOf(buildModel), TestModel::class.java)
      .executionMode(GradleExecutionMode.DEFAULT)
      .execute(modelConsumer)

    buildController.assertRunActionCounts(listOf(projectModels.size))
    buildController.assertModelRequests(testModels.map {
      TestModelRequest(it.key, TestModel::class.java)
    })

    modelConsumer.assertBuildModels(emptyList())
    modelConsumer.assertProjectModels(testModels.map {
      TestConsumedModel(it.key, it.value, TestModel::class.java)
    })
  }

  @Test
  fun `fetch models can pass model builder parameter`() {
    val rootProject = MockGradleProject("root")
    val subProject = MockGradleProject("sub-project", rootProject)
    val projectModels = listOf(rootProject, subProject)
    val buildModel = MockGradleBuild(rootProject, projectModels)

    val testModels = projectModels.associateWith { TestModel.Impl(it) }
    val buildController = TestBuildController().apply {
      registerModels(TestModel::class.java, testModels)
      registerParameter(TestModelParameter::class.java, TestModelParameter::Impl)
    }
    val modelConsumer = TestModelConsumer()

    GradleModelControllerImpl(buildController)
      .fetchRequest(listOf(buildModel), TestModel::class.java)
      .parameter(TestModelParameter::class.java) { it.value = "parameter-value" }
      .execute(modelConsumer)

    buildController.assertRunActionCounts(emptyList())
    buildController.assertModelRequests(testModels.map {
      TestModelRequest(it.key, TestModel::class.java, TestModelParameter::class.java, TestModelParameter.Impl("parameter-value"))
    })

    modelConsumer.assertBuildModels(emptyList())
    modelConsumer.assertProjectModels(testModels.map {
      TestConsumedModel(it.key, it.value, TestModel::class.java)
    })
  }

  @Test
  fun `resilient model fetch api handles model fetch failures`() {
    val rootProject = MockGradleProject("root")
    val subProject = MockGradleProject("sub-project", rootProject)
    val projectModels = listOf(rootProject, subProject)
    val buildModel = MockGradleBuild(rootProject, projectModels)

    val buildController = TestBuildController().apply {
      registerModelFailure(rootProject, TestModel::class.java, TestModelFetchException())
    }
    val modelConsumer = TestModelConsumer()
    val modelRequest = GradleModelControllerImpl(buildController)
      .fetchRequest(listOf(buildModel), TestModel::class.java)

    when (isResilientSyncEnabled) {
      true -> modelRequest.execute(modelConsumer)
      else -> assertThrows<TestModelFetchException> {
        modelRequest.execute(modelConsumer)
      }
    }

    buildController.assertRunActionCounts(emptyList())
    buildController.assertModelRequests(
      when (isResilientSyncEnabled) {
        true -> projectModels
        else -> listOf(rootProject)
      }.map {
        TestModelRequest(it, TestModel::class.java)
      }
    )

    modelConsumer.assertBuildModels(emptyList())
    modelConsumer.assertProjectModels(emptyList())
  }

  private class MockGradleBuild(
    private val rootProject: MockGradleProject?,
    private val projects: Collection<MockGradleProject>,
  ) : GradleBuild by notImplemented(GradleBuild::class.java) {
    override fun getRootProject() = rootProject
    override fun getProjects() = ImmutableDomainObjectSet.of(projects)!!
    override fun toString(): String = rootProject?.name.toString()
  }

  private class MockGradleProject(
    private val name: String,
    private val parent: MockGradleProject? = null,
    private val children: MutableList<MockGradleProject> = ArrayList(),
  ) : BasicGradleProject by notImplemented(BasicGradleProject::class.java) {
    override fun getName() = name
    override fun getParent() = parent
    override fun getChildren() = ImmutableDomainObjectSet.of(children)!!
    override fun toString(): String = name

    init {
      parent?.children?.add(this)
    }
  }

  private interface TestModel {
    val value: Any

    data class Impl(override val value: Any) : TestModel
  }

  private interface TestModelParameter {
    var value: Any?

    data class Impl(override var value: Any? = null) : TestModelParameter
  }

  private class TestModelFetchException : RuntimeException()
}
