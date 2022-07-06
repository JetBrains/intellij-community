// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.codegen

import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.util.io.assertMatches
import com.intellij.util.io.directoryContentOf
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import java.io.File
import java.nio.file.Path

class EntityCodeGenTest: KotlinLightCodeInsightFixtureTestCase() {
  private var genFolder: VirtualFile? = null

  override val testDataDirectory: File
    get() = File(PathManagerEx.getCommunityHomePath() + "/platform/workspaceModel/codegen/test/testData/$testDirectoryName")

  override fun setUp() {
    super.setUp()
    // Set strip trailing spaces
    val settings = EditorSettingsExternalizable.getInstance()
    settings.stripTrailingSpaces = EditorSettingsExternalizable.STRIP_TRAILING_SPACES_WHOLE

    runWriteActionAndWait {
      genFolder = createGeneratedSourceFolder()
    }
    val entityFolderVfu = VirtualFileManager.getInstance().findFileByNioPath(testDataDirectory.toPath().resolve("before"))!!
    runWriteActionAndWait {
      VfsUtil.copyDirectory(this, entityFolderVfu, getSourceRootVfu(), null)
    }
  }

  fun testSimpleCase() {
    doTest()
  }

  fun testFinalProperty() {
    doTest()
  }

  fun testDefaultProperty() {
    doTest()
  }

  private fun doTest() {
    runWriteActionAndWait {
      CodeWriter.generate(project, getSourceRootVfu(), false) { genFolder }
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
      VfsUtil.copyDirectory(this, getSourceRootVfu(), expectedDirVfu, null)
    }
    expectedDir.assertMatches(directoryContentOf(getExpectedDir()))
  }

  private fun createGeneratedSourceFolder(): VirtualFile {
    val generatedFolder = WriteAction.compute<VirtualFile, RuntimeException> {
      VfsUtil.createDirectoryIfMissing(getSourceRootVfu(), "gen")
    }

    val modifiableModel = ModuleRootManager.getInstance(module).modifiableModel
    val contentEntry = modifiableModel.contentEntries.first()
    contentEntry.addSourceFolder(generatedFolder, JavaSourceRootType.SOURCE,
                                 JpsJavaExtensionService.getInstance().createSourceRootProperties("", true))
    modifiableModel.commit()
    module.project.save()
    return generatedFolder
  }

  private fun getSourceRootVfu(): VirtualFile {
    val moduleRootManager = ModuleRootManager.getInstance(module)
    return moduleRootManager.contentEntries.first().sourceFolders.first().file!!
  }

  private fun getExpectedDir(): Path {
    return testDataDirectory.toPath().resolve("after")
  }
}