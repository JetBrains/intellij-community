// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.codegen

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.util.io.assertMatches
import com.intellij.util.io.directoryContentOf
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.KotlinLightProjectDescriptor
import java.io.File
import java.nio.file.Path

class EntityCodeGenTest: KotlinLightCodeInsightFixtureTestCase() {
  override val testDataDirectory: File
    get() = File(PathManagerEx.getCommunityHomePath() + "/platform/workspaceModel/codegen/test/testData/$testDirectoryName")

  override fun setUp() {
    super.setUp()
    val settings = EditorSettingsExternalizable.getInstance()
    val oldValue = settings.stripTrailingSpaces
    settings.stripTrailingSpaces = EditorSettingsExternalizable.STRIP_TRAILING_SPACES_WHOLE
    disposeOnTearDown(Disposable {
      settings.stripTrailingSpaces = oldValue
    })
    myFixture.copyDirectoryToProject("before", "")
  }

  override fun getProjectDescriptor(): LightProjectDescriptor = entitiesProjectDescriptor

  fun testSimpleCase() {
    doTest()
  }

  fun testFinalProperty() {
    doTest()
  }

  fun testDefaultProperty() {
    doTest()
  }

  fun testPersistentId() {
    doTest()
  }

  fun testEntityWithCollections() {
    doTest()
  }

  fun testRefsSetNotSupported() {
    assertThrows(IllegalStateException::class.java) { doTest() }
  }

  private fun doTest() {
    runWriteActionAndWait {
      CodeWriter.generate(project, myFixture.findFileInTempDir("."), false) { myFixture.tempDirFixture.findOrCreateDir("gen") }
      PsiDocumentManager.getInstance(project).commitAllDocuments()
      VirtualFileManager.getInstance().syncRefresh()
    }

    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()

    runWriteActionAndWait {
      FileDocumentManager.getInstance().saveAllDocuments()
    }

    val expectedDir = FileUtil.createTempDirectory(EntityCodeGenTest::class.java.simpleName, "${testDirectoryName}_api", true)
    val expectedDirVfu = VirtualFileManager.getInstance().findFileByNioPath(expectedDir.toPath())!!
    runWriteActionAndWait {
      VfsUtil.copyDirectory(this, myFixture.findFileInTempDir("."), expectedDirVfu, null)
    }
    expectedDir.assertMatches(directoryContentOf(getExpectedDir()))
  }


  private fun getExpectedDir(): Path {
    return testDataDirectory.toPath().resolve("after")
  }

  companion object {
    val entitiesProjectDescriptor: EntitiesProjectDescriptor by lazy { EntitiesProjectDescriptor() }
  }

  class EntitiesProjectDescriptor : KotlinLightProjectDescriptor() {
    override fun configureModule(module: Module, model: ModifiableRootModel) {
      val contentEntry = model.contentEntries.first()
      val genFolder = VfsUtil.createDirectoryIfMissing(contentEntry.file, "gen")

      contentEntry.addSourceFolder(genFolder, JavaSourceRootType.SOURCE,
                                   JpsJavaExtensionService.getInstance().createSourceRootProperties("", true))
    }
  }
}