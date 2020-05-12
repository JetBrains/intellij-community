// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspace.jps

import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.module.EmptyModuleType
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.rd.attach
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.TemporaryDirectory
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File

const val JAVA_CLASS_PREFIX = "JavaClass"
const val MODULE_PREFIX = "module"
const val TEST_MODULE_PREFIX = "test"


@RunWith(Parameterized::class)
class WorkspaceModelPerformanceTest(private val modulesCount: Int) {
  @Rule
  @JvmField
  var application = ApplicationRule()

  @Rule
  @JvmField
  var temporaryDirectoryRule = TemporaryDirectory()

  @Rule
  @JvmField
  var disposableRule = DisposableRule()

  private lateinit var project: Project

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun getModulesCount() = listOf(100, 1000/*, 5000, 10_000, 30_000, 50_000, 70_000, 100_000*/)
  }

  @Before
  fun prepareProject() {
    val projectDir = temporaryDirectoryRule.newPath("project").toFile()
    logExecutionTimeMillis("Project generation") {
      generateProject(projectDir)
    }
    project = loadTestProject(projectDir, disposableRule)
  }

  @Test
  fun `test base project actions`() = WriteCommandAction.runWriteCommandAction(project) {
    // To enable execution at old project model set -Dide.workspace.model.jps.enabled=false in Run Configuration
    val antLibName = "ant"
    val mavenLibName = "maven"
    val moduleManager = ModuleManager.getInstance(project)

    val modules = mutableListOf<Module>()
    logExecutionTimeMillis("Hundred modules creation") {
      (1..100).forEach {
        val modifiableModel = moduleManager.modifiableModel
        modules.add(modifiableModel.newModule(File(project.basePath, "$TEST_MODULE_PREFIX$it.iml").path, EmptyModuleType.getInstance().id))
        modifiableModel.commit()
      }
    }
    Assert.assertEquals(modulesCount + 100, moduleManager.modules.size)

    val library = createProjectLibrary(mavenLibName)
    logExecutionTimeMillis("Add project library at hundred modules") {
      modules.forEach { module ->
        ModuleRootManager.getInstance(module).modifiableModel.let {
          it.addLibraryEntry(library)
          it.commit()
        }
      }
    }

    logExecutionTimeMillis("Add module library at hundred modules") {
      modules.forEach { module -> ModuleRootModificationUtil.addModuleLibrary(module, antLibName, listOf(), emptyList()) }
    }

    logExecutionTimeMillis("Loop through the contentRoots of all modules") {
      moduleManager.modules.forEach { ModuleRootManager.getInstance(it).contentRoots.forEach { entry -> entry.canonicalFile } }
    }

    logExecutionTimeMillis("Loop through the orderEntries of all modules") {
      moduleManager.modules.forEach { ModuleRootManager.getInstance(it).orderEntries.forEach { entry -> entry.isValid }}
    }

    logExecutionTimeMillis("Find and remove project library from hundred modules") {
      modules.forEach { module ->
        ModuleRootManager.getInstance(module).modifiableModel.let {
          it.removeOrderEntry(it.findLibraryOrderEntry(library)!!)
          it.commit()
        }
      }
    }

    logExecutionTimeMillis("Find and remove module library from hundred modules") {
      modules.forEach { module ->
        ModuleRootManager.getInstance(module).modifiableModel.let {
          val moduleLibrary = it.moduleLibraryTable.getLibraryByName(antLibName)!!
          it.removeOrderEntry(it.findLibraryOrderEntry(moduleLibrary)!!)
          it.commit()
        }
      }
    }

    logExecutionTimeMillis("Hundred modules remove") {
      modules.forEach {
        val modifiableModel = moduleManager.modifiableModel
        modifiableModel.disposeModule(it)
        modifiableModel.commit()
      }
    }
    Assert.assertEquals(modulesCount, moduleManager.modules.size)
  }

  private fun loadTestProject(projectDir: File, disposableRule: DisposableRule): Project {
    val project = logExecutionTimeMillis<Project>("Project load") {
      return@logExecutionTimeMillis ProjectManager.getInstance().loadAndOpenProject(projectDir)!!
    }
    invokeAndWaitIfNeeded { ProjectManagerEx.getInstanceEx().openTestProject(project) }
    disposableRule.disposable.attach { invokeAndWaitIfNeeded { ProjectManagerEx.getInstanceEx().forceCloseProject(project) } }
    return project
  }

  private fun logExecutionTimeMillis(message: String, block: () -> Unit) {
    val start = System.currentTimeMillis()
    block()
    val end = System.currentTimeMillis() - start
    println("$message: ${end}ms")
  }

  private fun <T> logExecutionTimeMillis(message: String, block: () -> T): T {
    val start = System.currentTimeMillis()
    val result = block()
    val end = System.currentTimeMillis() - start
    println("$message: ${end}ms")
    return result
  }

  private fun createProjectLibrary(libraryName: String): Library {
    val projectLibraryTable = LibraryTablesRegistrar.getInstance().getLibraryTable(project)
    val library = projectLibraryTable.createLibrary(libraryName)

    library.modifiableModel.let {
      it.addRoot(File(project.basePath, "$libraryName.jar").path, OrderRootType.CLASSES)
      it.addRoot(File(project.basePath, "$libraryName-sources.jar").path, OrderRootType.SOURCES)
      it.commit()
    }
    return library
  }

  private fun generateProject(projectDir: File) {
    createIfNotExist(projectDir)
    for (index in 1..modulesCount) {
      val moduleName = "$MODULE_PREFIX$index"
      val module = File(projectDir, moduleName)
      createIfNotExist(module)
      val src = File(module, "src")
      createIfNotExist(src)

      val moduleNumbers = generateModuleNumbers(index)
      val javaFile = File(src, "$JAVA_CLASS_PREFIX$index.java")
      javaFile.writeText(generateJavaFileContent(moduleNumbers, index))

      val moduleIml = File(module, "$moduleName.iml")
      moduleIml.writeText(generateImlFileContent(moduleNumbers, index))
    }
    val ideaFolder = File(projectDir, ".idea")
    createIfNotExist(ideaFolder)
    val miscFile = File(ideaFolder, "misc.xml")
    miscFile.writeText("""<?xml version="1.0" encoding="UTF-8"?>
<project version="4">
   <component name="ProjectRootManager" version="2" languageLevel="JDK_1_8" default="true" project-jdk-name="1.8" project-jdk-type="JavaSDK">
     <output url="file://${'$'}PROJECT_DIR${'$'}/out" />
   </component>
</project>""")
    val modulesFile = File(ideaFolder, "modules.xml")
    modulesFile.writeText(generateModulesFileContent())
  }

  private fun generateModulesFileContent(): String {
    val builder = StringBuilder()
    builder.append("""<?xml version="1.0" encoding="UTF-8"?>
<project version="4">
  <component name="ProjectModuleManager">
    <modules>
""")
    for (index in 1..modulesCount) {
      builder.append("      <module fileurl=\"file://${'$'}PROJECT_DIR${'$'}/$MODULE_PREFIX$index/$MODULE_PREFIX$index.iml\" " +
                     "filepath=\"${'$'}PROJECT_DIR${'$'}/$MODULE_PREFIX$index/$MODULE_PREFIX$index.iml\" />\n")
    }
    builder.append("""
     </modules>
  </component>
</project>""")
    return builder.toString()
  }

  private fun generateJavaFileContent(modules: Set<Int>, moduleNumber: Int): String {
    val builder = StringBuilder()
    builder.append("public class $JAVA_CLASS_PREFIX$moduleNumber {\n")
    modules.forEach {
      if (it != moduleNumber) builder.append("      private $JAVA_CLASS_PREFIX$it field$it = new JavaClass$it(); \n")
    }
    builder.append("""
         public $JAVA_CLASS_PREFIX$moduleNumber() {
             System.out.println("Hello From $moduleNumber");
         }
    """)
    builder.append("\n}")
    return builder.toString()
  }

  private fun generateImlFileContent(modules: Set<Int>, moduleNumber: Int): String {
    val builder = StringBuilder()
    builder.append("""<?xml version="1.0" encoding="UTF-8"?>
<module type="JAVA_MODULE" version="4">
    <component name="NewModuleRootManager" inherit-compiler-output="true">
        <exclude-output />
        <content url="file://${'$'}MODULE_DIR${'$'}">
            <sourceFolder url="file://${'$'}MODULE_DIR${'$'}/src" isTestSource="false" />
        </content>
        <orderEntry type="inheritedJdk" />
        <orderEntry type="sourceFolder" forTests="false" />
""")
    modules.forEach {
      if (it != moduleNumber) {
        builder.append("      <orderEntry type=\"module\" module-name=\"$MODULE_PREFIX$it\" /> \n")
      }
    }
    builder.append("    </component>\n</module>")
    return builder.toString()
  }

  private fun generateModuleNumbers(moduleNumber: Int): Set<Int> {
    val to = moduleNumber - 1
    if (to <= 0) return setOf()
    val from = if (to - 10 <= 0) 1 else to - 10
    val result = mutableSetOf<Int>()
    for (index in from..to) {
      result.add(index)
    }
    return result
  }

  private fun createIfNotExist(dir: File) {
    if (!dir.exists()) dir.mkdir()
  }
}