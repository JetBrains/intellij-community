// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.ide

import com.intellij.openapi.application.impl.ApplicationInfoImpl
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.module.EmptyModuleType
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.impl.ModuleManagerComponent
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.TemporaryDirectory
import com.intellij.testFramework.rules.ProjectModelRule
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.ModuleManagerComponentBridge
import com.intellij.workspaceModel.storage.WorkspaceEntityStorageBuilder
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleDependencyItem
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleEntity
import com.intellij.workspaceModel.storage.bridgeEntities.addModuleEntity
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File
import java.nio.file.Path

private const val JAVA_CLASS_PREFIX = "JavaClass"
private const val MODULE_PREFIX = "module"
private const val TEST_MODULE_PREFIX = "test"

@Ignore
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

  private var disposerDebugMode = true

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun getModulesCount() = listOf(100, 1000/*, 5000, 10_000, 30_000, 50_000, 70_000, 100_000*/)
  }

  @Before
  fun prepareProject() {
    ApplicationInfoImpl.setInStressTest(true)

    disposerDebugMode = Disposer.isDebugMode()
    Disposer.setDebugMode(false)

    val projectDir = temporaryDirectoryRule.newPath("project")
    logExecutionTimeInMillis("Project generation") {
      generateProject(projectDir.toFile())
    }
    project = loadTestProject(projectDir, disposableRule)
  }

  fun tearDown() {
    ApplicationInfoImpl.setInStressTest(false)
    Disposer.setDebugMode(disposerDebugMode)
  }

  @Test
  fun `test base project actions`() = WriteCommandAction.runWriteCommandAction(project) {
    // To enable execution at new project model set -Dide.new.project.model=true in Run Configuration
    // To enable execution at old project model set -Dide.workspace.model.jps.enabled=false in Run Configuration
    val antLibName = "ant"
    val mavenLibName = "maven"
    val moduleManager = ModuleManager.getInstance(project)

    when (moduleManager) {
      is ModuleManagerComponentBridge -> "Legacy bridge model enabled: $moduleManager"
      is ModuleManagerComponent -> "Old model enabled: $moduleManager"
      else -> "Unknown model enabled: $moduleManager"
    }.also { println(it) }

    val modules = mutableListOf<Module>()
    logExecutionTimeInMillis("Hundred modules creation") { hundredModulesCreation(moduleManager, modules) }
    assertEquals(modulesCount + 100, moduleManager.modules.size)

    val library = createProjectLibrary(mavenLibName)
    logExecutionTimeInMillis("Add project library at hundred modules") { addProjectLibraryToHundredModules(modules, library) }

    logExecutionTimeInMillis("Add module library at hundred modules") {
      addModuleLibraryToHundredModules(modules, antLibName)
    }

    logExecutionTimeInMillis("Loop through the contentRoots of all modules") {
      loopThroughContentRootsOfAllModules(moduleManager)
    }

    logExecutionTimeInMillis("Loop through the orderEntries of all modules") {
      loopThroughOrderEntriesOfAllModules(moduleManager)
    }

    logExecutionTimeInMillis("Find and remove project library from hundred modules") {
      findAndRemoveProjectLibFromHundredModules(modules, library)
    }

    logExecutionTimeInMillis("Find and remove module library from hundred modules") {
      findAndRemoveLibFromHundredModules(modules, antLibName)
    }

    logExecutionTimeInMillis("Hundred modules remove") {
      hundredModulesRemove(modules, moduleManager)
    }
    assertEquals(modulesCount, moduleManager.modules.size)
  }

  private fun hundredModulesRemove(modules: MutableList<Module>,
                                   moduleManager: ModuleManager) {
    modules.forEach {
      val modifiableModel = moduleManager.modifiableModel
      modifiableModel.disposeModule(it)
      modifiableModel.commit()
    }
  }

  private fun findAndRemoveLibFromHundredModules(modules: MutableList<Module>, antLibName: String) {
    modules.forEach { module ->
      ModuleRootManager.getInstance(module).modifiableModel.let {
        val moduleLibrary = it.moduleLibraryTable.getLibraryByName(antLibName)!!
        it.removeOrderEntry(it.findLibraryOrderEntry(moduleLibrary)!!)
        it.commit()
      }
    }
  }

  private fun findAndRemoveProjectLibFromHundredModules(modules: MutableList<Module>,
                                                        library: Library) {
    modules.forEach { module ->
      ModuleRootManager.getInstance(module).modifiableModel.let {
        it.removeOrderEntry(it.findLibraryOrderEntry(library)!!)
        it.commit()
      }
    }
  }

  private fun loopThroughOrderEntriesOfAllModules(moduleManager: ModuleManager) {
    moduleManager.modules.forEach { ModuleRootManager.getInstance(it).orderEntries.forEach { entry -> entry.isValid } }
  }

  private fun loopThroughContentRootsOfAllModules(moduleManager: ModuleManager) {
    moduleManager.modules.forEach { ModuleRootManager.getInstance(it).contentRoots.forEach { entry -> entry.canonicalFile } }
  }

  private fun addModuleLibraryToHundredModules(modules: MutableList<Module>, antLibName: String) {
    modules.forEach { module -> ModuleRootModificationUtil.addModuleLibrary(module, antLibName, listOf(), emptyList()) }
  }

  private fun addProjectLibraryToHundredModules(modules: MutableList<Module>,
                                                library: Library) {
    modules.forEach { module ->
      ModuleRootManager.getInstance(module).modifiableModel.let {
        it.addLibraryEntry(library)
        it.commit()
      }
    }
  }

  private fun hundredModulesCreation(moduleManager: ModuleManager,
                                     modules: MutableList<Module>) {
    (1..100).forEach {
      val modifiableModel = moduleManager.modifiableModel
      modules.add(modifiableModel.newModule(File(project.basePath, "$TEST_MODULE_PREFIX$it.iml").path, EmptyModuleType.getInstance().id))
      modifiableModel.commit()
    }
  }

  @Test
  fun `test base operations in store`()  = WriteCommandAction.runWriteCommandAction(project) {
    if (!ProjectModelRule.isWorkspaceModelEnabled) return@runWriteCommandAction

    val workspaceModel = WorkspaceModel.getInstance(project)
    var diff = WorkspaceEntityStorageBuilder.from(workspaceModel.entityStorage.current)

    val moduleType = EmptyModuleType.getInstance().id
    val dependencies = listOf(ModuleDependencyItem.ModuleSourceDependency)
    logExecutionTimeInMillis("Hundred entries creation in store") {
      (1..100).forEach {
        diff.addModuleEntity("$TEST_MODULE_PREFIX$it", dependencies, NonPersistentEntitySource, moduleType)
      }
      workspaceModel.updateProjectModel { it.addDiff(diff) }
    }

    var entities = workspaceModel.entityStorage.current.entities(ModuleEntity::class.java)
    logExecutionTimeInInNano("Loop thorough the entities from store") { entities.forEach { it.name } }
    assertEquals(modulesCount + 100, entities.toList().size)

    diff = WorkspaceEntityStorageBuilder.from(workspaceModel.entityStorage.current)
    logExecutionTimeInMillis("Remove hundred entities from store") {
      workspaceModel.entityStorage.current.entities(ModuleEntity::class.java).take(100).forEach { diff.removeEntity(it) }
      workspaceModel.updateProjectModel { it.addDiff(diff) }
    }

    entities = workspaceModel.entityStorage.current.entities(ModuleEntity::class.java)
    assertEquals(modulesCount, entities.toList().size)
  }

  private fun loadTestProject(projectDir: Path, disposableRule: DisposableRule): Project {
    val project = logExecutionTimeInMillis<Project>("Project load") {
      PlatformTestUtil.loadAndOpenProject(projectDir)
    }
    disposableRule.register {
      PlatformTestUtil.forceCloseProjectWithoutSaving(project)
    }
    return project
  }

  private fun logExecutionTimeInMillis(message: String, block: () -> Unit) {
    val start = System.currentTimeMillis()
    block()
    val end = System.currentTimeMillis() - start
    println("$message: ${end}ms")
  }

  private fun logExecutionTimeInInNano(message: String, block: () -> Unit) {
    val start = System.nanoTime()
    block()
    val end = System.nanoTime() - start
    println("$message: ${end}ns")
  }

  private fun <T> logExecutionTimeInMillis(message: String, block: () -> T): T {
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