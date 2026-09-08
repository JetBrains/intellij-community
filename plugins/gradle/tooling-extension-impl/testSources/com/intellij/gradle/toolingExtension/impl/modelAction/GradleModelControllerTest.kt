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
import com.intellij.platform.testFramework.assertion.collectionAssertion.CollectionAssertion
import com.intellij.testFramework.common.mock.notImplemented
import com.intellij.testFramework.junit5.TestDisposable
import org.assertj.core.api.Assertions.assertThat
import org.gradle.api.Action
import org.gradle.tooling.model.BuildIdentifier
import org.gradle.tooling.model.gradle.BasicGradleProject
import org.gradle.tooling.model.gradle.GradleBuild
import org.gradle.tooling.model.internal.ImmutableDomainObjectSet
import java.io.File
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.junitpioneer.jupiter.cartesian.CartesianTest
import kotlin.to

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

    val testModels = projectModels.map { it to TestModel.Impl(it) }
    val buildController = TestBuildController().apply {
      registerModels(TestModel::class.java, testModels)
    }
    val modelConsumer = TestModelConsumer()

    GradleModelControllerImpl(buildController)
      .fetchRequest(listOf(buildModel), TestModel::class.java)
      .traversalMode(GradleTraversalMode.DIRECT)
      .execute(modelConsumer)

    buildController.assertModelRequests(testModels.map {
      TestModelRequest(it.first, TestModel::class.java)
    })

    modelConsumer.assertNoBuildModels()
    modelConsumer.assertProjectModels(testModels.map {
      TestConsumedModel(it.first, it.second, TestModel::class.java)
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
    val projectModelsRecursive = listOf(rootProject, subProject1, subProject2, subSubProject1, subSubProject2)
    val buildModel = MockGradleBuild(rootProject, projectModels)

    val testModelsRecursive = projectModelsRecursive.map { it to TestModel.Impl(it) }
    val buildController = TestBuildController().apply {
      registerModels(TestModel::class.java, testModelsRecursive)
    }
    val modelConsumer = TestModelConsumer()

    GradleModelControllerImpl(buildController)
      .fetchRequest(listOf(buildModel), TestModel::class.java)
      .traversalMode(GradleTraversalMode.RECURSIVE)
      .execute(modelConsumer)

    buildController.assertModelRequests(testModelsRecursive.map {
      TestModelRequest(it.first, TestModel::class.java)
    })

    modelConsumer.assertNoBuildModels()
    modelConsumer.assertProjectModels(testModelsRecursive.map {
      TestConsumedModel(it.first, it.second, TestModel::class.java)
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

    val testModelsRecursive = projectModelsRecursive.map { it to TestModel.Impl(it) }
    val buildController = TestBuildController().apply {
      registerModels(TestModel::class.java, testModelsRecursive)
    }
    val modelConsumer = TestModelConsumer()

    GradleModelControllerImpl(buildController)
      .fetchRequest(listOf(buildModel), TestModel::class.java)
      .traversalMode(GradleTraversalMode.RECURSIVE)
      .execute(modelConsumer)

    buildController.assertModelRequests(testModelsRecursive.map {
      TestModelRequest(it.first, TestModel::class.java)
    })

    modelConsumer.assertNoBuildModels()
    modelConsumer.assertProjectModels(testModelsRecursive.map {
      TestConsumedModel(it.first, it.second, TestModel::class.java)
    })
  }

  @Test
  fun `fetch models can target builds`() {
    val buildModel1 = MockGradleBuild(MockGradleProject("root-1"))
    val buildModel2 = MockGradleBuild(MockGradleProject("root-2"))
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

    buildController.assertModelRequests(
      TestModelRequest(buildModel1, String::class.java),
      TestModelRequest(buildModel2, String::class.java)
    )

    modelConsumer.assertNoProjectModels()
    modelConsumer.assertBuildModels(
      TestConsumedModel(buildModel1, "build-model-1", String::class.java),
      TestConsumedModel(buildModel2, "build-model-2", String::class.java),
    )
  }

  @CartesianTest
  fun `fetch models can run in parallel`(
    @CartesianTest.Enum executionMode: GradleExecutionMode,
    @CartesianTest.Values(booleans = [true, false]) parallelModelFetch: Boolean,
    @TestDisposable disposable: Disposable,
  ) {
    setSystemProperty(PARALLEL_MODEL_FETCH_PROPERTY_KEY, parallelModelFetch.toString(), disposable)

    val rootProject = MockGradleProject("root")
    val subProject = MockGradleProject("sub-project", rootProject)
    val buildModel = MockGradleBuild(rootProject, listOf(rootProject, subProject))

    val rootProjectModel = TestModel.Impl(rootProject)
    val subProjectModel = TestModel.Impl(subProject)

    val buildController = TestBuildController().apply {
      registerModel(rootProject, TestModel::class.java, rootProjectModel)
      registerModel(subProject, TestModel::class.java, subProjectModel)
    }
    val modelConsumer = TestModelConsumer()

    GradleModelControllerImpl(buildController)
      .fetchRequest(listOf(buildModel), TestModel::class.java)
      .executionMode(executionMode)
      .execute(modelConsumer)

    when (executionMode) {
      GradleExecutionMode.DEFAULT -> when (parallelModelFetch) {
        true -> buildController.assertRunActions(buildModel.projects.size)
        else -> buildController.assertNoRunActions()
      }
      GradleExecutionMode.PARALLEL -> buildController.assertRunActions(buildModel.projects.size)
      GradleExecutionMode.SEQUENTIAL -> buildController.assertNoRunActions()
    }

    buildController.assertModelRequests(
      TestModelRequest(rootProject, TestModel::class.java),
      TestModelRequest(subProject, TestModel::class.java)
    )

    modelConsumer.assertNoBuildModels()
    modelConsumer.assertProjectModels(
      TestConsumedModel(rootProject, rootProjectModel, TestModel::class.java),
      TestConsumedModel(subProject, subProjectModel, TestModel::class.java)
    )
  }

  @Test
  fun `fetch models can pass model builder parameter`() {
    val rootProject = MockGradleProject("root")
    val subProject = MockGradleProject("sub-project", rootProject)
    val projectModels = listOf(rootProject, subProject)
    val buildModel = MockGradleBuild(rootProject, projectModels)

    val rootProjectModel = TestModel.Impl(rootProject)
    val subProjectModel = TestModel.Impl(subProject)
    val buildController = TestBuildController().apply {
      registerModel(rootProject, TestModel::class.java, rootProjectModel)
      registerModel(subProject, TestModel::class.java, subProjectModel)
    }
    val modelConsumer = TestModelConsumer()

    val parameterInitializer = Action<TestModelParameter> { it.value = "parameter-value" }

    GradleModelControllerImpl(buildController)
      .fetchRequest(listOf(buildModel), TestModel::class.java)
      .parameter(TestModelParameter::class.java, parameterInitializer)
      .execute(modelConsumer)

    buildController.assertModelRequests(
      TestModelRequest(rootProject, TestModel::class.java, TestModelParameter::class.java, parameterInitializer),
      TestModelRequest(subProject, TestModel::class.java, TestModelParameter::class.java, parameterInitializer)
    )

    modelConsumer.assertNoBuildModels()
    modelConsumer.assertProjectModels(
      TestConsumedModel(rootProject, rootProjectModel, TestModel::class.java),
      TestConsumedModel(subProject, subProjectModel, TestModel::class.java)
    )
  }

  @ParameterizedTest
  @ValueSource(booleans = [true, false])
  fun `resilient model fetch api handles model fetch failures`(suppressFailures: Boolean) {
    val rootProject = MockGradleProject("root")
    val subProject = MockGradleProject("sub-project", rootProject)
    val buildModel = MockGradleBuild(rootProject, listOf(rootProject, subProject))

    val buildController = TestBuildController().apply {
      registerModelFailure(rootProject, TestModel::class.java, TestModelFetchException())
      registerModelFailure(subProject, TestModel::class.java, TestModelFetchException())
    }
    val modelConsumer = TestModelConsumer()
    val modelRequest = GradleModelControllerImpl(buildController)
      .fetchRequest(listOf(buildModel), TestModel::class.java)
      .suppressFailures(suppressFailures)

    when (suppressFailures) {
      true -> modelRequest.execute(modelConsumer)
      else -> when (isResilientSyncEnabled) {
        true -> modelRequest.execute(modelConsumer)
        else -> assertThrows<TestModelFetchException> {
          modelRequest.execute(modelConsumer)
        }
      }
    }

    when (suppressFailures) {
      true -> buildController.assertModelRequests(
        TestModelRequest(rootProject, TestModel::class.java),
        TestModelRequest(subProject, TestModel::class.java)
      )
      else -> when (isResilientSyncEnabled) {
        true -> buildController.assertModelRequests(
          TestModelRequest(rootProject, TestModel::class.java),
          TestModelRequest(subProject, TestModel::class.java)
        )
        else -> buildController.assertModelRequests(
          TestModelRequest(rootProject, TestModel::class.java)
        )
      }
    }

    when (suppressFailures) {
      true -> buildController.assertNoSentFailures()
      else -> when (isResilientSyncEnabled) {
        true -> buildController.assertSentFailureExceptions {
          modelFetchFailure(TestModelFetchException::class.java)
          modelFetchFailure(TestModelFetchException::class.java)
        }
        else -> buildController.assertNoSentFailures()
      }
    }

    modelConsumer.assertNoBuildModels()
    modelConsumer.assertNoProjectModels()
  }

  @Test
  fun `resilient model fetch api propagates project directory as target path in failure result`() {
    val rootProjectDir = File("root-dir")
    val subProjectDir = rootProjectDir.resolve("sub-project")
    val rootProject = MockGradleProject("root", projectDirectory = rootProjectDir)
    val subProject = MockGradleProject("sub-project", rootProject, projectDirectory = subProjectDir)
    val buildModel = MockGradleBuild(rootProject, listOf(rootProject, subProject))

    val buildController = TestBuildController().apply {
      registerModelFailure(rootProject, TestModel::class.java, TestModelFetchException())
      registerModelFailure(subProject, TestModel::class.java, TestModelFetchException())
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

    when (isResilientSyncEnabled) {
      true -> buildController.assertModelRequests(
        TestModelRequest(rootProject, TestModel::class.java),
        TestModelRequest(subProject, TestModel::class.java)
      )
      else -> buildController.assertModelRequests(
        TestModelRequest(rootProject, TestModel::class.java),
      )
    }
    when (isResilientSyncEnabled) {
      true -> {
        buildController.assertSentFailureTargetPaths(rootProjectDir, subProjectDir)
        buildController.assertSentFailureExceptions {
          modelFetchFailure(TestModelFetchException::class.java)
          modelFetchFailure(TestModelFetchException::class.java)
        }
      }
      else -> buildController.assertNoSentFailures()
    }

    modelConsumer.assertNoBuildModels()
    modelConsumer.assertNoProjectModels()
  }

  @Test
  fun `resilient model fetch api propagates build root directory as target path in failure result`() {
    val buildRootDir = File("build-root-dir")
    val rootProject = MockGradleProject("root")
    val buildModel = MockGradleBuild(rootProject, buildRootDir = buildRootDir)

    val buildController = TestBuildController().apply {
      registerModelFailure(buildModel, TestModel::class.java, TestModelFetchException())
    }

    val modelConsumer = TestModelConsumer()

    val modelRequest = GradleModelControllerImpl(buildController)
      .fetchRequest(listOf(buildModel), TestModel::class.java)
      .modelLevel(GradleModelLevel.BUILD)

    when (isResilientSyncEnabled) {
      true -> modelRequest.execute(modelConsumer)
      else -> assertThrows<TestModelFetchException> {
        modelRequest.execute(modelConsumer)
      }
    }

    buildController.assertModelRequests(
      TestModelRequest(buildModel, TestModel::class.java),
    )
    when (isResilientSyncEnabled) {
      true -> {
        buildController.assertSentFailureTargetPaths(buildRootDir)
        buildController.assertSentFailureExceptions {
          modelFetchFailure(TestModelFetchException::class.java)
        }
      }
      else -> buildController.assertNoSentFailures()
    }

    modelConsumer.assertNoBuildModels()
    modelConsumer.assertNoProjectModels()
  }

  private class MockGradleBuild(
    private val rootProject: MockGradleProject?,
    private val projects: Collection<MockGradleProject> = listOf(rootProject!!),
    private val buildRootDir: File = rootProject?.projectDirectory ?: File("build"),
  ) : GradleBuild by notImplemented(GradleBuild::class.java) {
    override fun getRootProject() = rootProject
    override fun getProjects() = ImmutableDomainObjectSet.of(projects)!!
    override fun getBuildIdentifier() = BuildIdentifier { buildRootDir }
    override fun toString(): String = rootProject?.name.toString()
  }

  private class MockGradleProject(
    private val name: String,
    private val parent: MockGradleProject? = null,
    private val projectDirectory: File = File(name),
  ) : BasicGradleProject by notImplemented(BasicGradleProject::class.java) {
    private val children = ArrayList<MockGradleProject>()

    override fun getName() = name
    override fun getParent() = parent
    override fun getChildren() = ImmutableDomainObjectSet.of(children)!!
    override fun getProjectDirectory() = projectDirectory
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

  companion object {

    fun <T : Exception> CollectionAssertion<GradleModelFetchFailure>.modelFetchFailure(exceptionClass: Class<T>) {
      assertElement { assertThat(it.description).startsWith(exceptionClass.name) }
    }
  }
}
