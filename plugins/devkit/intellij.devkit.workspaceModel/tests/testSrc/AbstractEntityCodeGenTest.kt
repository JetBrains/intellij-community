// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.workspaceModel

import com.intellij.devkit.workspaceModel.codegen.writer.CodeWriter
import com.intellij.ide.impl.OpenProjectTask
import com.intellij.java.workspace.entities.JavaSourceRootPropertiesEntity
import com.intellij.java.workspace.entities.javaSourceRoots
import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.modules
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.util.io.findOrCreateDirectory
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.toVirtualFileUrl
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.jps.entities.ContentRootEntity
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.ModuleSourceDependency
import com.intellij.platform.workspace.jps.entities.SourceRootEntity
import com.intellij.pom.PomManager
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.junit5.fixture.disposableFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.testFramework.junit5.fixture.testNameFixture
import com.intellij.util.io.assertMatches
import com.intellij.util.io.directoryContentOf
import com.intellij.workspaceModel.ide.NonPersistentEntitySource
import com.intellij.workspaceModel.ide.legacyBridge.impl.java.JAVA_SOURCE_ROOT_ENTITY_TYPE_ID
import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlin.idea.test.enableKotlinOfficialCodeStyle
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.nio.file.Path

abstract class AbstractEntityCodeGenTest {
  private val tempPath = tempPathFixture(prefix = this.javaClass.simpleName)
  private val project = projectFixture(pathFixture = tempPath, openProjectTask = OpenProjectTask { createModule = false }, openAfterCreation = true)
  private val testName = testNameFixture()
  private val disposable = disposableFixture()

  val testDataDirectory: Path
    get() = Path.of(PathManagerEx.getCommunityHomePath() + "/plugins/devkit/intellij.devkit.workspaceModel/tests/testData/codeGen/${testName.get()}")

  val actualSrcRoot: VirtualFile
    get() = VfsUtil.findFile(tempPath.get().resolve("src").findOrCreateDirectory(), true)!!

  val actualGenRoot: VirtualFile
    get() = VfsUtil.findFile(tempPath.get().resolve("gen").findOrCreateDirectory(), true)!!

  @BeforeEach
  fun setUp(): Unit = runBlocking {
    enableKotlinOfficialCodeStyle(project.get())
    val jdk = IdeaTestUtil.getMockJdk21()
    writeAction {
      ProjectJdkTable.getInstance().addJdk(jdk, disposable.get())
    }
    val projectRoot = VfsUtil.findFile(tempPath.get(), true)!!

    val testPath = VfsUtil.findFile(testDataDirectory.resolve("before"), true)!!
    writeAction {
      VfsUtil.copyDirectory(this, testPath, actualSrcRoot, null)
    }

    val wsm = project.get().workspaceModel
    wsm.update("Setup project roots") {
      val module = ModuleEntity("${this@AbstractEntityCodeGenTest.javaClass.simpleName}_${testName}_module", listOf(ModuleSourceDependency), NonPersistentEntitySource)
      val contentRoot = ContentRootEntity(projectRoot.toVirtualFileUrl(wsm.getVirtualFileUrlManager()), emptyList(), NonPersistentEntitySource)
      module.contentRoots += contentRoot
      contentRoot.sourceRoots += SourceRootEntity(actualSrcRoot.toVirtualFileUrl(wsm.getVirtualFileUrlManager()), JAVA_SOURCE_ROOT_ENTITY_TYPE_ID, NonPersistentEntitySource)
      val genSourceRoot = SourceRootEntity(actualGenRoot.toVirtualFileUrl(wsm.getVirtualFileUrlManager()), JAVA_SOURCE_ROOT_ENTITY_TYPE_ID, NonPersistentEntitySource)
      genSourceRoot.javaSourceRoots += JavaSourceRootPropertiesEntity(generated = true, packagePrefix = "", entitySource = NonPersistentEntitySource)
      contentRoot.sourceRoots += genSourceRoot

      it.addEntity(module)
    }
    val module = project.get().modules.first()
    val model = readAction { ModuleRootManager.getInstance(module).getModifiableModel() }

    LibrariesRequiredForWorkspace.workspaceStorage.add(model)
    LibrariesRequiredForWorkspace.workspaceJpsEntities.add(model)

    writeAction {
      model.sdk = jdk
      model.commit()
    }

    IndexingTestUtil.waitUntilIndexesAreReady(project.get())

    // Load codegen jar on warm-up phase
    CodegenJarLoader.getInstance(project.get()).getClassLoader()
    PomManager.getModel(project.get()) // initialize PostprocessReformattingAspectImpl to enable reformatting after PSI changes
  }

  @Test
  fun testSimpleCase() {
    doTest()
  }

  /**
   * Tests that code is formatted properly: codestyle, copyright is added, imports are optimized.
   */
  @Test
  fun testFormat() {
    doTest(formatCode = true)
  }

  @Test
  fun testSimpleNonWsmExtension() {
    doTest()
  }

  @Test
  fun testFinalProperty() {
    doTest()
  }

  @Test
  fun testDefaultProperty() {
    doTest()
  }

  @Test
  fun testSymbolicId() {
    doTest()
  }

  @Test
  fun testUpdateOldCode() {
    doTest()
  }

  @Test
  fun testEntityWithCollections() {
    doTest()
  }

  @Test
  fun testEntityWithChildrenCollection() {
    doTest()
  }

  @Test
  fun testEntityWithDifferentChildrenTargets() {
    doTest()
  }

  @Test
  fun testEntityWithSelfReference() {
    doTest()
  }

  @Test
  fun testRefsFromAnotherModule() {
    doTest()
  }

  @Test
  fun testRefsSetNotSupported() {
    assertThrows<IllegalStateException> { doTest() }
  }

  @Test
  fun testHierarchyOfEntities() {
    doTest()
  }

  @Test
  fun testVirtualFileUrls() {
    doTest()
  }

  @Test
  fun testUnknownPropertyType() {
    doTest(processAbstractTypes = true)
  }

  @Test
  fun testOpenClassProperty() {
    doTest(processAbstractTypes = true)
  }

  @Test
  fun testPackages() {
    doTest()
  }

  @Test
  fun testPropertiesOrder() {
    doTest()
  }

  @Test
  fun testCompatibilityInvoke() {
    doTest()
  }

  @Test
  fun testBothLinksAreParents() {
    doTestAndCheckErrorMessage("Both fields MainEntity#secondaryEntity and SecondaryEntity#mainEntity are marked as parent. Probably both properties are annotated with @Parent, while only one should be.")
  }

  @Test
  fun testBothLinksAreChildren() {
    doTestAndCheckErrorMessage("Failed to generate code for secondaryEntities (MainEntity): Both fields MainEntity#secondaryEntities and SecondaryEntity#mainEntity are marked as child. Probably @Parent annotation is missing from one of the properties.")
  }

  @Test
  fun testChildrenShouldBeNullable() {
    doTestAndCheckErrorMessage("Failed to generate code for secondaryEntity (MainEntity): Child references should always be nullable")
  }

  @Test
  fun testVarFieldForbidden() {
    doTestAndCheckErrorMessage("Failed to generate code for isValid (MainEntity): An immutable interface can't contain mutable properties")
  }

  @Test
  fun testSymbolicIdNotDeclared() {
    doTestAndCheckErrorMessage("Failed to generate code for SimpleSymbolicIdEntity: Class extends 'WorkspaceEntityWithSymbolicId' but doesn't override 'WorkspaceEntityWithSymbolicId.getSymbolicId' property")
  }

  @Test
  fun testInheritanceEntityAndSource() {
    doTestAndCheckErrorMessage("Failed to collect metadata: com.intellij.workspaceModel.test.api.IllegalEntity should not extend WorkspaceEntity and EntitySource at the same time")
  }

  @Test
  fun testInheritanceMultiple() {
    doTestAndCheckErrorMessage("Failed to collect metadata: com.intellij.workspaceModel.test.api.MultipleInheritanceEntity should not extend multiple @Abstract entities: AbstractEntity3, AnotherAbstractEntity")
  }

  @Test
  fun testInheritanceNonAbstract() {
    doTestAndCheckErrorMessage("Failed to generate code for IllegalEntity: Class 'LegalEntity' cannot be extended")
  }

  @Test
  fun testVisibilityModifier() {
    doTest()
  }

  private fun doTestAndCheckErrorMessage(expectedMessage: String) {
    val exception = Assertions.assertThrows(IllegalStateException::class.java) {
      doTest()
    }
    val actualMessage = exception.message!!
    assertEquals(expectedMessage, actualMessage)
  }

  private fun doTest(processAbstractTypes: Boolean = false, formatCode: Boolean = false) {
    runBlocking {
      CodeWriter.generate(project = project.get(),
                          module = project.get().modules.first(),
                          actualSrcRoot,
                          processAbstractTypes = processAbstractTypes,
                          explicitApiEnabled = false,
                          isTestSourceFolder = false,
                          isTestModule = false,
                          targetFolderGenerator = { actualGenRoot },
                          existingTargetFolder = { actualGenRoot },
                          formatCode = formatCode)
    }

    FileDocumentManager.getInstance().saveAllDocuments()

    Path.of(actualGenRoot.path).assertMatches(directoryContentOf(testDataDirectory.resolve("after/gen")))
  }
}
