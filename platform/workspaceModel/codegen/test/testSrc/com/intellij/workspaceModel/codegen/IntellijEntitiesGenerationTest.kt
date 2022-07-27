// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.codegen

import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFileFilter
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.util.SystemProperties
import java.io.File
import java.nio.file.Path

class IntellijEntitiesGenerationTest : CodeGenerationTestBase() {
  private enum class IntellijEntitiesPackage(val apiRelativePath: String, val implRelativePath: String, val keepPropertiesWithUnknownType: Boolean = false) {
    Bridges("platform/workspaceModel/storage/src/com/intellij/workspaceModel/storage",
            "platform/workspaceModel/storage/gen/com/intellij/workspaceModel/storage"),
    Eclipse("plugins/eclipse/src/org/jetbrains/idea/eclipse/workspaceModel",
            "plugins/eclipse/gen/org/jetbrains/idea/eclipse/workspaceModel"),
    Tests("platform/workspaceModel/storage/testEntities/testSrc/com/intellij/workspaceModel/storage/entities/test/api",
          "platform/workspaceModel/storage/testEntities/gen/com/intellij/workspaceModel/storage/entities/test/api", true),
    UnknownPropertyType("platform/workspaceModel/storage/testEntities/testSrc/com/intellij/workspaceModel/storage/entities/unknowntypes/test/api",
          "platform/workspaceModel/storage/testEntities/gen/com/intellij/workspaceModel/storage/entities/unknowntypes/test/api", true);
    
    val apiPath: Path
      get() = Path.of(PlatformTestUtil.getCommunityPath(), apiRelativePath)

    val implPath: Path
      get() = Path.of(PlatformTestUtil.getCommunityPath(), implRelativePath)
  }
  
  override val testDataDirectory: File
    get() = File(PlatformTestUtil.getCommunityPath())

  fun `test bridge entities generation`() {
    doTest(IntellijEntitiesPackage.Bridges)
  }

  fun `test eclipse entities generation`() {
    doTest(IntellijEntitiesPackage.Eclipse)
  }

  fun `test test entities generation`() {
    doTest(IntellijEntitiesPackage.Tests)
  }

  fun `test unknown property type entities generation`() {
    doTest(IntellijEntitiesPackage.UnknownPropertyType)
  }

  fun `test update code`() {
    val propertyKey = "intellij.workspace.model.update.entities"
    if (!SystemProperties.getBooleanProperty(propertyKey, false)) {
      println("Set ${propertyKey} system property to 'true' to update entities code in the sources")
      return
    }
    
    for (entitiesPackage in IntellijEntitiesPackage.values()) {
      val packageName = entitiesPackage.name
      myFixture.copyDirectoryToProject(entitiesPackage.apiRelativePath, packageName)
      val (srcRoot, genRoot) = generateCode(packageName, entitiesPackage.keepPropertiesWithUnknownType)
      runWriteActionAndWait {
        val apiDir = VirtualFileManager.getInstance().refreshAndFindFileByNioPath(entitiesPackage.apiPath)!!
        val implDir = VirtualFileManager.getInstance().refreshAndFindFileByNioPath(entitiesPackage.implPath)!!
        VfsUtil.copyDirectory(this, srcRoot, apiDir, VirtualFileFilter { it != genRoot })
        VfsUtil.copyDirectory(this, genRoot, implDir, null)
      }
    }
  }

  private fun doTest(entitiesPackage: IntellijEntitiesPackage) {
    myFixture.copyDirectoryToProject(entitiesPackage.apiRelativePath, "")
    generateAndCompare(entitiesPackage.apiPath, entitiesPackage.implPath, entitiesPackage.keepPropertiesWithUnknownType)
  }
}
