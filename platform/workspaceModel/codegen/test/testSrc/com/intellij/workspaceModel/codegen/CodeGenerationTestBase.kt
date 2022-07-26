// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.codegen

import com.intellij.application.options.CodeStyle
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileFilter
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.codeStyle.CommonCodeStyleSettings
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.util.PathUtil
import com.intellij.util.io.assertMatches
import com.intellij.util.io.directoryContentOf
import com.intellij.workspaceModel.storage.WorkspaceEntity
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.core.formatter.KotlinCodeStyleSettings
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.KotlinLightProjectDescriptor
import java.nio.file.Path

open class CodeGenerationTestBase : KotlinLightCodeInsightFixtureTestCase() {
  override fun setUp() {
    super.setUp()
    val settings = EditorSettingsExternalizable.getInstance()
    val oldTrailingSpacesValue = settings.stripTrailingSpaces
    settings.stripTrailingSpaces = EditorSettingsExternalizable.STRIP_TRAILING_SPACES_WHOLE
    
    //set up code style accordingly to settings used in intellij project to ensure that generated code follows it 
    val codeStyleSettings = CodeStyle.createTestSettings()
    val kotlinCommonSettings = codeStyleSettings.getCommonSettings(KotlinLanguage.INSTANCE)
    kotlinCommonSettings.ELSE_ON_NEW_LINE = true
    kotlinCommonSettings.LINE_COMMENT_AT_FIRST_COLUMN = false
    kotlinCommonSettings.KEEP_FIRST_COLUMN_COMMENT = false
    kotlinCommonSettings.ALIGN_MULTILINE_PARAMETERS_IN_CALLS = true
    kotlinCommonSettings.ALIGN_MULTILINE_BINARY_OPERATION = true
    kotlinCommonSettings.ALIGN_MULTILINE_EXTENDS_LIST = true
    kotlinCommonSettings.METHOD_PARAMETERS_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED or CommonCodeStyleSettings.WRAP_ON_EVERY_ITEM
    kotlinCommonSettings.CALL_PARAMETERS_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED
    kotlinCommonSettings.RIGHT_MARGIN = 140
    codeStyleSettings.getCustomSettings(KotlinCodeStyleSettings::class.java).LINE_BREAK_AFTER_MULTILINE_WHEN_ENTRY = false
    val indentOptions = codeStyleSettings.getIndentOptions(KotlinFileType.INSTANCE)
    indentOptions.INDENT_SIZE = 2
    indentOptions.TAB_SIZE = 2
    indentOptions.CONTINUATION_INDENT_SIZE = 2
    CodeStyle.setTemporarySettings(project, codeStyleSettings)
    disposeOnTearDown(Disposable {
      settings.stripTrailingSpaces = oldTrailingSpacesValue
    })
  }

  override fun tearDown() {
    try {
      CodeStyle.dropTemporarySettings(project)
    }
    catch (e: Throwable) {
      addSuppressedException(e)
    }
    finally {
      super.tearDown()
    }
  }

  override fun getProjectDescriptor(): LightProjectDescriptor = WorkspaceEntitiesProjectDescriptor
  
  protected fun generateAndCompare(dirWithExpectedApiFiles: Path, dirWithExpectedImplFiles: Path, keepUnknownFields: Boolean = false) {
    val (srcRoot, genRoot) = generateCode(".", keepUnknownFields)

    val expectedApiDirPath = FileUtil.createTempDirectory(CodeGenerationTestBase::class.java.simpleName, "${testDirectoryName}_api", true)
    val expectedApiDir = VirtualFileManager.getInstance().findFileByNioPath(expectedApiDirPath.toPath())!!
    if (FileUtil.isAncestor(dirWithExpectedApiFiles, dirWithExpectedImplFiles, true)) {
      runWriteActionAndWait {
        VfsUtil.copyDirectory(this, srcRoot, expectedApiDir, null)
      }
      expectedApiDirPath.assertMatches(directoryContentOf(dirWithExpectedApiFiles))
    }
    else {
      val expectedImplDirPath: Path = FileUtil.createTempDirectory(CodeGenerationTestBase::class.java.simpleName, "${testDirectoryName}_impl", true).toPath()
      val expectedImplDir = VirtualFileManager.getInstance().findFileByNioPath(expectedImplDirPath)!!
      runWriteActionAndWait {
        VfsUtil.copyDirectory(this, srcRoot, expectedApiDir, VirtualFileFilter { it != genRoot })
        VfsUtil.copyDirectory(this, genRoot, expectedImplDir, null)
      }
      expectedApiDirPath.assertMatches(directoryContentOf(dirWithExpectedApiFiles))
      expectedImplDirPath.assertMatches(directoryContentOf(dirWithExpectedImplFiles))
    }
  }

  protected fun generateCode(relativePathToEntitiesDirectory: String, keepUnknownFields: Boolean = false): Pair<VirtualFile, VirtualFile> {
    val srcRoot = myFixture.findFileInTempDir(relativePathToEntitiesDirectory)
    val genRoot = myFixture.tempDirFixture.findOrCreateDir("gen/$relativePathToEntitiesDirectory")
    runWriteActionAndWait {
      CodeWriter.generate(project, srcRoot, keepUnknownFields) { genRoot }
      FileDocumentManager.getInstance().saveAllDocuments()
    }
    return srcRoot to genRoot
  }

  object WorkspaceEntitiesProjectDescriptor : KotlinLightProjectDescriptor() {
    override fun configureModule(module: Module, model: ModifiableRootModel) {
      val contentEntry = model.contentEntries.first()
      val genFolder = VfsUtil.createDirectoryIfMissing(contentEntry.file, "gen")

      contentEntry.addSourceFolder(genFolder, JavaSourceRootType.SOURCE,
                                   JpsJavaExtensionService.getInstance().createSourceRootProperties("", true))
      val library = model.moduleLibraryTable.modifiableModel.createLibrary("workspace-storage")
      val modifiableModel = library.modifiableModel
      modifiableModel.addRoot(VfsUtil.pathToUrl(PathUtil.getJarPathForClass(WorkspaceEntity::class.java)), OrderRootType.CLASSES)
      modifiableModel.commit()
    }
  }
}