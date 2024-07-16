// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5.showcase.resources.full

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.resources.asExtension
import com.intellij.testFramework.junit5.resources.create
import com.intellij.testFramework.junit5.resources.providers.PathInfo
import com.intellij.testFramework.junit5.resources.providers.ProjectProvider
import com.intellij.testFramework.junit5.resources.providers.module.ModuleName
import com.intellij.testFramework.junit5.resources.providers.module.ModuleParams
import com.intellij.testFramework.junit5.resources.providers.module.ModulePersistenceType.Persistent
import com.intellij.testFramework.junit5.resources.providers.module.ModuleProvider
import com.intellij.testFramework.junit5.resources.providers.module.ProjectSource.ExplicitProject
import com.intellij.testFramework.junit5.resources.providers.module.ProjectSource.ProjectFromExtension
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.name
import kotlin.io.path.pathString

/**
 * Everything created manually here for fine-tuning freaks.
 * [allModulesClearListener] checks that modules do not leak
 */
@TestApplication
class JUnit5Manually {

  @JvmField
  @Order(0)
  @RegisterExtension
  val allModulesClearListener = AfterEachCallback {
    assertTrue(modules.all { it.isDisposed })
    assertTrue(modules.all { it.project.isDisposed })
  }
  private val projectName = "myProject${hashCode()}"
  private val explicitProjectPath = Files.createTempDirectory(projectName)
  private val explicitModulePath = Files.createTempDirectory(projectName)

  @JvmField
  @RegisterExtension
  @Order(1)
  val projectExt = ProjectProvider { PathInfo(explicitProjectPath) }.asExtension()


  @JvmField
  @RegisterExtension
  @Order(2)
  val moduleExt = ModuleProvider {
    ModuleParams(
      name = ModuleName("${Math.random()}MyModule${Math.random()}"),
      modulePersistenceType = Persistent { _, name ->
        PathInfo(explicitModulePath.resolve(name.name), deletePathOnExit = true, closeFsOnExit = false)
      },
      projectSource = ProjectFromExtension
    )
  }.asExtension()

  private val modules = mutableListOf<Module>()

  @AfterEach
  fun deletePath() {
    Files.deleteIfExists(explicitProjectPath)
    Files.deleteIfExists(explicitModulePath)
  }

  @Test
  fun ensureModuleObeyParams(module: Module) {
    assertTrue(module.moduleNioFile.pathString.startsWith(explicitModulePath.pathString)) {
      "Module $module sits in a wrong dir"
    }
  }

  @Test
  fun ensureAutoProjectObeysParams(project: Project) {
    val projectDir = project.guessProjectDir()!!.toNioPath().name
    assertTrue(projectDir.startsWith(projectName)) {
      "$projectDir is not correct"
    }
  }


  @Test
  fun createManually(@TempDir projectPath: Path): Unit = runBlocking {
    val project1 = projectExt.create(PathInfo((projectPath)))
    val project2 = projectExt.create(PathInfo(projectPath))
    assertNotEquals(project1, project2)

    val module = moduleExt.create(ModuleParams(
      name = ModuleName("MyModule"),
      modulePersistenceType = Persistent(),
      projectSource = ExplicitProject(project1)
    ))
    assertEquals(module.project, project1)

    val module2 = moduleExt.create()
    assertNotEquals(module, module2)
    modules.add(module)
    modules.add(module2)
  }

}