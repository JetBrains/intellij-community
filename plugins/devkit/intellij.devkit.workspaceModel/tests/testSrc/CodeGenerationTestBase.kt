// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.workspaceModel

import com.intellij.application.options.CodeStyle
import com.intellij.devkit.workspaceModel.codegen.writer.CodeWriter
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileFilter
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.codeStyle.CommonCodeStyleSettings
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.util.io.assertMatches
import com.intellij.util.io.directoryContentOf
import kotlinx.coroutines.runBlocking
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.core.formatter.KotlinCodeStyleSettings
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.KotlinLightProjectDescriptor
import java.nio.file.Path

abstract class CodeGenerationTestBase : KotlinLightCodeInsightFixtureTestCase() {
  protected val INDENT_SIZE = 2
  protected val TAB_SIZE = 2
  protected val CONTINUATION_INDENT_SIZE = 2

  override fun setUp() {
    super.setUp()
    // Load codegen jar on warm-up phase
    runBlocking {
      CodegenJarLoader.getInstance(project).getClassLoader()
    }
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
    val kotlinFileType: LanguageFileType? = KotlinFileType.INSTANCE
    val indentOptions = codeStyleSettings.getIndentOptions(kotlinFileType)
    indentOptions.INDENT_SIZE = INDENT_SIZE
    indentOptions.TAB_SIZE = TAB_SIZE
    indentOptions.CONTINUATION_INDENT_SIZE = CONTINUATION_INDENT_SIZE
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

  override fun getProjectDescriptor(): LightProjectDescriptor = WorkspaceEntitiesProjectDescriptor(shouldAddWorkspaceStorageLibrary,
                                                                                                   shouldAddWorkspaceJpsEntityLibrary)

  /**
   * Returns `true` if compiled content of intellij.platform.workspaceModel.storage should be added as a library.
   */
  protected open val shouldAddWorkspaceStorageLibrary: Boolean
    get() = true

  /**
   * Returns `true` if compiled content of intellij.platform.workspace.jps should be added as a library.
   */
  protected open val shouldAddWorkspaceJpsEntityLibrary: Boolean
    get() = true

  protected fun generateAndCompare(
    dirWithExpectedApiFiles: Path, dirWithExpectedImplFiles: Path,
    pathToPackage: String = ".",
    processAbstractTypes: Boolean, explicitApiEnabled: Boolean,
    isTestModule: Boolean
  ) {
    val (srcRoot, genRoot) = generateCode(
      relativePathToEntitiesDirectory = ".",
      processAbstractTypes = processAbstractTypes,
      explicitApiEnabled = explicitApiEnabled,
      isTestModule = isTestModule
    )

    val srcPackageDir = srcRoot.findFileByRelativePath(pathToPackage) ?: error("Cannot find $pathToPackage under $srcRoot")
    val genPackageDir = genRoot.findFileByRelativePath(pathToPackage) ?: error("Cannot find $pathToPackage under $genRoot")

    val expectedApiDirPath = FileUtil.createTempDirectory(CodeGenerationTestBase::class.java.simpleName, "${testDirectoryName}_api", true)
    val expectedApiDir = VirtualFileManager.getInstance().refreshAndFindFileByNioPath(expectedApiDirPath.toPath())!!
    if (dirWithExpectedImplFiles.startsWith(dirWithExpectedApiFiles) && dirWithExpectedImplFiles != dirWithExpectedApiFiles) {
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

  protected fun generateCode(
    relativePathToEntitiesDirectory: String, processAbstractTypes: Boolean, explicitApiEnabled: Boolean, isTestModule: Boolean
  ): Pair<VirtualFile, VirtualFile> {
    val srcRoot = myFixture.findFileInTempDir(relativePathToEntitiesDirectory)
    val genRoot = myFixture.tempDirFixture.findOrCreateDir("gen/$relativePathToEntitiesDirectory")
    runBlocking {
      CodeWriter.generate(
        project, module, srcRoot,
        processAbstractTypes = processAbstractTypes,
        explicitApiEnabled = explicitApiEnabled,
        isTestSourceFolder = false,
        isTestModule = isTestModule,
        targetFolderGenerator = { genRoot },
        existingTargetFolder = { genRoot }
      )
      FileDocumentManager.getInstance().saveAllDocuments()
    }
    return srcRoot to genRoot
  }

  class WorkspaceEntitiesProjectDescriptor(
    private val addWorkspaceStorageLibrary: Boolean,
    private val addWorkspaceJpsEntityLibrary: Boolean
  ) : KotlinLightProjectDescriptor() {
    override fun configureModule(module: Module, model: ModifiableRootModel) {
      val contentEntry = model.contentEntries.first()
      val genFolder = VfsUtil.createDirectoryIfMissing(contentEntry.file, "gen")

      contentEntry.addSourceFolder(genFolder, JavaSourceRootType.SOURCE,
                                   JpsJavaExtensionService.getInstance().createSourceRootProperties("", true))
      if (addWorkspaceStorageLibrary) {
        LibrariesRequiredForWorkspace.workspaceStorage.add(model)
      }
      if (addWorkspaceJpsEntityLibrary) {
        LibrariesRequiredForWorkspace.workspaceJpsEntities.add(model)
      }
    }

    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (javaClass != other?.javaClass) return false

      other as WorkspaceEntitiesProjectDescriptor

      if (addWorkspaceStorageLibrary != other.addWorkspaceStorageLibrary) return false
      if (addWorkspaceJpsEntityLibrary != other.addWorkspaceJpsEntityLibrary) return false

      return true
    }

    override fun hashCode(): Int {
      var result = addWorkspaceStorageLibrary.hashCode()
      result = 31 * result + addWorkspaceJpsEntityLibrary.hashCode()
      return result
    }
  }
}
