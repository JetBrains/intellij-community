// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.workspaceModel

import com.intellij.application.options.CodeStyle
import com.intellij.devkit.workspaceModel.codegen.writer.CodeWriter
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileFilter
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.codeStyle.CommonCodeStyleSettings
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.VfsTestUtil
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

abstract class CodeGenerationTestBase : KotlinLightCodeInsightFixtureTestCase() {
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

  override fun getProjectDescriptor(): LightProjectDescriptor = WorkspaceEntitiesProjectDescriptor(shouldAddWorkspaceStorageLibrary)

  /**
   * Returns `true` if compiled content of intellij.platform.workspaceModel.storage should be added as a library. 
   */
  protected open val shouldAddWorkspaceStorageLibrary: Boolean
    get() = true

  protected fun generateAndCompare(dirWithExpectedApiFiles: Path, dirWithExpectedImplFiles: Path, keepUnknownFields: Boolean = false,
                                   pathToPackage: String = ".") {
    val (srcRoot, genRoot) = generateCode(".", keepUnknownFields)
    val srcPackageDir = srcRoot.findFileByRelativePath(pathToPackage) ?: error("Cannot find $pathToPackage under $srcRoot")
    val genPackageDir = genRoot.findFileByRelativePath(pathToPackage) ?: error("Cannot find $pathToPackage under $genRoot")

    val expectedApiDirPath = FileUtil.createTempDirectory(CodeGenerationTestBase::class.java.simpleName, "${testDirectoryName}_api", true)
    val expectedApiDir = VirtualFileManager.getInstance().refreshAndFindFileByNioPath(expectedApiDirPath.toPath())!!
    if (FileUtil.isAncestor(dirWithExpectedApiFiles, dirWithExpectedImplFiles, true)) {
      runWriteActionAndWait {
        VfsUtil.copyDirectory(this, srcPackageDir, expectedApiDir, null)
      }
      expectedApiDirPath.assertMatches(directoryContentOf(dirWithExpectedApiFiles))
    }
    else {
      val expectedImplDirPath: Path = FileUtil.createTempDirectory(CodeGenerationTestBase::class.java.simpleName, "${testDirectoryName}_impl", true).toPath()
      val expectedImplDir = VirtualFileManager.getInstance().refreshAndFindFileByNioPath(expectedImplDirPath)!!
      runWriteActionAndWait {
        VfsUtil.copyDirectory(this, srcPackageDir, expectedApiDir, VirtualFileFilter { it != genRoot })
        VfsUtil.copyDirectory(this, genPackageDir, expectedImplDir, null)
      }
      expectedApiDirPath.assertMatches(directoryContentOf(dirWithExpectedApiFiles))
      expectedImplDirPath.assertMatches(directoryContentOf(dirWithExpectedImplFiles))
    }
  }

  protected fun generateCode(relativePathToEntitiesDirectory: String, keepUnknownFields: Boolean = false): Pair<VirtualFile, VirtualFile> {
    val srcRoot = myFixture.findFileInTempDir(relativePathToEntitiesDirectory)
    val genRoot = myFixture.tempDirFixture.findOrCreateDir("gen/$relativePathToEntitiesDirectory")
    val keepUnknownFieldsValue = Registry.get("workspace.model.generator.keep.unknown.fields")
    keepUnknownFieldsValue.setValue(keepUnknownFields)
    try {
      runWriteActionAndWait {
        CodeWriter.generate(project, module, srcRoot) { genRoot }
        FileDocumentManager.getInstance().saveAllDocuments()
      }
    }
    finally {
      keepUnknownFieldsValue.setValue(false)
    }
    return srcRoot to genRoot
  }

  class WorkspaceEntitiesProjectDescriptor(private val addWorkspaceStorageLibrary: Boolean) : KotlinLightProjectDescriptor() {
    override fun configureModule(module: Module, model: ModifiableRootModel) {
      val contentEntry = model.contentEntries.first()
      val genFolder = VfsUtil.createDirectoryIfMissing(contentEntry.file, "gen")

      contentEntry.addSourceFolder(genFolder, JavaSourceRootType.SOURCE,
                                   JpsJavaExtensionService.getInstance().createSourceRootProperties("", true))
      if (addWorkspaceStorageLibrary) {
        addWorkspaceStorageLibrary(model)
      }
    }

    override fun equals(other: Any?): Boolean {
      return other is WorkspaceEntitiesProjectDescriptor && addWorkspaceStorageLibrary == other.addWorkspaceStorageLibrary
    }

    override fun hashCode(): Int {
      return addWorkspaceStorageLibrary.hashCode()
    }
  }

  companion object {
    internal fun removeWorkspaceStorageLibrary(model: ModifiableRootModel) {
      val moduleLibraryTable = model.moduleLibraryTable
      val modifiableModel = model.moduleLibraryTable.modifiableModel
      modifiableModel.removeLibrary(moduleLibraryTable.libraries.first())
      modifiableModel.commit()
    }

    internal fun addWorkspaceStorageLibrary(model: ModifiableRootModel) {
      val library = model.moduleLibraryTable.modifiableModel.createLibrary("workspace-storage")
      val modifiableModel = library.modifiableModel
      val workspaceStorageClassesPath = VfsUtil.pathToUrl(PathUtil.getJarPathForClass(WorkspaceEntity::class.java))
      val workspaceStorageClassesRoot = VirtualFileManager.getInstance().refreshAndFindFileByUrl(workspaceStorageClassesPath)
      assertNotNull("Cannot find $workspaceStorageClassesPath", workspaceStorageClassesRoot)
      VfsUtil.markDirtyAndRefresh(false, true, true, workspaceStorageClassesRoot)
      modifiableModel.addRoot(workspaceStorageClassesRoot!!, OrderRootType.CLASSES)
      modifiableModel.commit()
    }
  }
}