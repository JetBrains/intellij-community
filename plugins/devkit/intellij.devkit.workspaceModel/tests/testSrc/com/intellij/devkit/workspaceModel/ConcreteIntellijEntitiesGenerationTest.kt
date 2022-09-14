// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.workspaceModel

import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFileFilter
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.util.SystemProperties
import com.intellij.workspaceModel.storage.CodeGeneratorVersions
import java.io.File
import java.nio.file.Path

class ConcreteIntellijEntitiesGenerationTest : CodeGenerationTestBase() {

  override fun setUp() {
    CodeGeneratorVersions.checkApiInInterface = false
    CodeGeneratorVersions.checkApiInImpl = false
    CodeGeneratorVersions.checkImplInImpl = false
    super.setUp()
  }

  override fun tearDown() {
    super.tearDown()
    CodeGeneratorVersions.checkApiInInterface = true
    CodeGeneratorVersions.checkApiInImpl = true
    CodeGeneratorVersions.checkImplInImpl = true
  }

  private enum class IntellijEntitiesPackage(val apiRootRelativePath: String, val implRootRelativePath: String, val pathToPackage: String, val keepPropertiesWithUnknownType: Boolean = false) {
    Bridges("platform/workspaceModel/storage/src",
            "platform/workspaceModel/storage/gen",
            "com/intellij/workspaceModel"),
    Eclipse("plugins/eclipse/src",
            "plugins/eclipse/gen",
            "org/jetbrains/idea/eclipse/workspaceModel"),
    Tests("platform/workspaceModel/storage/testEntities/testSrc",
          "platform/workspaceModel/storage/testEntities/gen",
          "com/intellij/workspaceModel/storage/entities/test/api", true);
    
    val apiRootPath: Path
      get() = Path.of(PlatformTestUtil.getCommunityPath(), apiRootRelativePath, pathToPackage)

    val implRootPath: Path
      get() = Path.of(PlatformTestUtil.getCommunityPath(), implRootRelativePath, pathToPackage)
  }
  
  override val testDataDirectory: File
    get() = File(PlatformTestUtil.getCommunityPath())

  override val shouldAddWorkspaceStorageLibrary: Boolean
    /** These tests include sources of intellij.platform.workspaceModel.storage module, so adding the same classes as a library will lead to errors */
    get() = name !in setOf("test bridge entities generation", "test update code")

  fun `test bridge entities generation`() {
    doTest(IntellijEntitiesPackage.Bridges)
  }

  fun `test eclipse entities generation`() {
    doTest(IntellijEntitiesPackage.Eclipse)
  }

  fun `test test entities generation`() {
    doTest(IntellijEntitiesPackage.Tests)
  }

  fun `test update code`() {
    val propertyKey = "intellij.workspace.model.update.entities"
    if (!SystemProperties.getBooleanProperty(propertyKey, false)) {
      println("Set ${propertyKey} system property to 'true' to update entities code in the sources")
      return
    }
    
    for (entitiesPackage in IntellijEntitiesPackage.values()) {
      val packagePath = entitiesPackage.pathToPackage.replace(".", "/")
      myFixture.copyDirectoryToProject(entitiesPackage.apiRootRelativePath, "")
      val (srcRoot, genRoot) = generateCode(packagePath, entitiesPackage.keepPropertiesWithUnknownType)
      runWriteActionAndWait {
        val apiDir = VirtualFileManager.getInstance().refreshAndFindFileByNioPath(entitiesPackage.apiRootPath)!!
        val implDir = VirtualFileManager.getInstance().refreshAndFindFileByNioPath(entitiesPackage.implRootPath)!!
        VfsUtil.copyDirectory(this, srcRoot, apiDir, VirtualFileFilter { it != genRoot })
        VfsUtil.copyDirectory(this, genRoot, implDir, null)
      }
    }
  }

  private fun doTest(entitiesPackage: IntellijEntitiesPackage) {
    myFixture.copyDirectoryToProject(entitiesPackage.apiRootRelativePath, "")
    generateAndCompare(entitiesPackage.apiRootPath, entitiesPackage.implRootPath, entitiesPackage.keepPropertiesWithUnknownType, entitiesPackage.pathToPackage)
  }
}
