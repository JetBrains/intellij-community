// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl.jps.serialization

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.components.CustomImlComponentService
import com.intellij.openapi.components.getComponentValue
import com.intellij.openapi.components.service
import com.intellij.openapi.module.EmptyModuleType
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.platform.workspace.jps.serialization.CustomImlComponentNameContributor
import com.intellij.project.stateStore
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.TemporaryDirectoryExtension
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.registerOrReplaceServiceInstance
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.nio.file.Files
import java.nio.file.Path

@TestApplication
class CustomImlComponentServiceTest {
  @JvmField
  @RegisterExtension
  val tempDir = TemporaryDirectoryExtension()

  @TestDisposable
  private lateinit var disposable: Disposable

  @Test
  fun `reads and writes state for multiple custom iml components`() {

    registerContributor(FirstTestModuleService.COMPONENT_NAME, disposable)
    registerContributor(SecondTestModuleService.COMPONENT_NAME, disposable)

    val projectPath = tempDir.newPath("project")
    Files.createDirectories(projectPath)

    openProject(projectPath) { project, _ ->
      runWriteAction {
        ModuleManager.getInstance(project).newModule(projectPath.resolve(MODULE_FILE_NAME), EmptyModuleType.EMPTY_MODULE)
      }
      saveProject(project)
    }

    openProject(projectPath) { project, openDisposable ->
      val module = ModuleManager.getInstance(project).modules.single()
      registerTestServices(module, openDisposable)
      val firstService = module.getService(FirstTestModuleService::class.java)!!
      val secondService = module.getService(SecondTestModuleService::class.java)!!
      val componentService = project.service<CustomImlComponentService>()

      val updatedFirstState = FirstTestModuleService.State("updated-first", false)
      val updatedSecondState = SecondTestModuleService.State(11, "updated-second")

      runBlocking {
        firstService.setState(updatedFirstState)
        secondService.setState(updatedSecondState)
      }

      assertEquals(updatedFirstState,
                   firstService.getState())
      assertEquals(updatedSecondState,
                   secondService.getState())
      assertEquals(updatedFirstState,
                   componentService.getComponentValue<FirstTestModuleService.State>(module,
                                                                                    FirstTestModuleService.COMPONENT_NAME))
      assertEquals(updatedSecondState,
                   componentService.getComponentValue<SecondTestModuleService.State>(module,
                                                                                     SecondTestModuleService.COMPONENT_NAME))

      saveProject(project)
    }

    openProject(projectPath) { project, openDisposable ->
      val module = ModuleManager.getInstance(project).modules.single()
      registerTestServices(module, openDisposable)
      val firstService = module.getService(FirstTestModuleService::class.java)!!
      val secondService = module.getService(SecondTestModuleService::class.java)!!

      assertEquals(FirstTestModuleService.State("updated-first", false), firstService.getState())
      assertEquals(SecondTestModuleService.State(11, "updated-second"), secondService.getState())
    }
  }

  @Test
  fun `reads and writes different values for same custom component in different modules`() {
    registerContributor(FirstTestModuleService.COMPONENT_NAME, disposable)

    val projectPath = tempDir.newPath("multi-module-project")
    Files.createDirectories(projectPath)

    val firstModuleState = FirstTestModuleService.State("first-module", true)
    val secondModuleState = FirstTestModuleService.State("second-module", false)

    openProject(projectPath) { project, _ ->
      runWriteAction {
        val moduleManager = ModuleManager.getInstance(project)
        moduleManager.newModule(projectPath.resolve(FIRST_MODULE_FILE_NAME), EmptyModuleType.EMPTY_MODULE)
        moduleManager.newModule(projectPath.resolve(SECOND_MODULE_FILE_NAME), EmptyModuleType.EMPTY_MODULE)
      }
      saveProject(project)
    }

    openProject(projectPath) { project, openDisposable ->
      val firstModule = getModuleByFileName(project, FIRST_MODULE_FILE_NAME)
      val secondModule = getModuleByFileName(project, SECOND_MODULE_FILE_NAME)
      registerTestServices(firstModule, openDisposable)
      registerTestServices(secondModule, openDisposable)

      val firstService = firstModule.getService(FirstTestModuleService::class.java)!!
      val secondService = secondModule.getService(FirstTestModuleService::class.java)!!
      val componentService = project.service<CustomImlComponentService>()

      runBlocking {
        firstService.setState(firstModuleState)
        secondService.setState(secondModuleState)
      }

      assertEquals(firstModuleState, firstService.getState())
      assertEquals(secondModuleState, secondService.getState())
      assertEquals(firstModuleState,
                   componentService.getComponentValue<FirstTestModuleService.State>(firstModule,
                                                                                    FirstTestModuleService.COMPONENT_NAME))
      assertEquals(secondModuleState,
                   componentService.getComponentValue<FirstTestModuleService.State>(secondModule,
                                                                                    FirstTestModuleService.COMPONENT_NAME))

      saveProject(project)
    }

    openProject(projectPath) { project, openDisposable ->
      val firstModule = getModuleByFileName(project, FIRST_MODULE_FILE_NAME)
      val secondModule = getModuleByFileName(project, SECOND_MODULE_FILE_NAME)
      registerTestServices(firstModule, openDisposable)
      registerTestServices(secondModule, openDisposable)

      val firstService = firstModule.getService(FirstTestModuleService::class.java)!!
      val secondService = secondModule.getService(FirstTestModuleService::class.java)!!

      assertEquals(firstModuleState, firstService.getState())
      assertEquals(secondModuleState, secondService.getState())
    }
  }

  @Test
  fun `returns null instead of throwing when stored component cannot be deserialized`() {
    registerContributor(SecondTestModuleService.COMPONENT_NAME, disposable)

    val projectPath = tempDir.newPath("corrupt-component-project")
    Files.createDirectories(projectPath)

    openProject(projectPath) { project, _ ->
      runWriteAction {
        ModuleManager.getInstance(project).newModule(projectPath.resolve(MODULE_FILE_NAME), EmptyModuleType.EMPTY_MODULE)
      }
      saveProject(project)
    }

    openProject(projectPath) { project, _ ->
      val module = ModuleManager.getInstance(project).modules.single()
      val componentService = project.service<CustomImlComponentService>()

      runBlocking {
        componentService.setComponentValue(module, SecondTestModuleService.COMPONENT_NAME, CorruptTestState("not-a-number"))
      }

      assertNull(componentService.getComponentValue<SecondTestModuleService.State>(module, SecondTestModuleService.COMPONENT_NAME))
    }
  }

  private fun registerContributor(componentName: String, disposable: Disposable) {
    BaseIdeSerializationContext.CUSTOM_IML_COMPONENT_NAME_CONTRIBUTOR_EP.point
      .registerExtension(TestCustomImlComponentNameContributor(componentName), disposable)
  }

  private fun registerTestServices(module: Module, disposable: Disposable) {
    module.registerOrReplaceServiceInstance(FirstTestModuleService::class.java, FirstTestModuleService(module), disposable)
    module.registerOrReplaceServiceInstance(SecondTestModuleService::class.java, SecondTestModuleService(module), disposable)
  }

  private fun getModuleByFileName(project: Project, moduleFileName: String): Module {
    return ModuleManager.getInstance(project).modules.single {
      Path.of(it.moduleFilePath).fileName.toString() == moduleFileName
    }
  }

  private fun openProject(projectPath: Path, action: (Project, Disposable) -> Unit) {
    val disposable = Disposer.newDisposable()
    try {
      val project = PlatformTestUtil.loadAndOpenProject(projectPath, disposable)
      action(project, disposable)
    }
    finally {
      Disposer.dispose(disposable)
    }
  }

  private fun saveProject(project: Project) {
    runBlocking {
      project.stateStore.save()
    }
  }

  private companion object {
    const val MODULE_FILE_NAME = "custom-module.iml"
    const val FIRST_MODULE_FILE_NAME = "first-module.iml"
    const val SECOND_MODULE_FILE_NAME = "second-module.iml"
  }
}

private class TestCustomImlComponentNameContributor(
  override val componentName: String,
) : CustomImlComponentNameContributor

/**
 * Serializes a `count` that is not an `Int`, so deserializing it back into [SecondTestModuleService.State]
 * (whose `count` is an `Int`) fails — mimicking a stored value incompatible with the current state schema.
 */
internal data class CorruptTestState(var count: String = "")

internal class FirstTestModuleService(private val module: Module) {
  data class State(
    var value: String = "",
    var enabled: Boolean = false,
  )

  fun getState(): State {
    return module.project.service<CustomImlComponentService>().getComponentValue(module, COMPONENT_NAME) ?: State()
  }

  suspend fun setState(state: State) {
    module.project.service<CustomImlComponentService>().setComponentValue(module, COMPONENT_NAME, state)
  }

  companion object {
    const val COMPONENT_NAME = "FirstCustomImlTestModuleService"
  }
}

internal class SecondTestModuleService(private val module: Module) {
  data class State(
    var count: Int = 0,
    var message: String = "",
  )

  fun getState(): State {
    return module.project.service<CustomImlComponentService>().getComponentValue(module, COMPONENT_NAME) ?: State()
  }

  suspend fun setState(state: State) {
    module.project.service<CustomImlComponentService>().setComponentValue(module, COMPONENT_NAME, state)
  }

  companion object {
    const val COMPONENT_NAME = "SecondCustomImlTestModuleService"
  }
}
