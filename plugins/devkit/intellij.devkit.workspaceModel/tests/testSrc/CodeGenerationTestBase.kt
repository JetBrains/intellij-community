// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.workspaceModel

import com.intellij.application.options.CodeStyle
import com.intellij.devkit.workspaceModel.codegen.writer.CodeWriter
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.IntelliJProjectUtil
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileFilter
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiManager
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.util.io.assertMatches
import com.intellij.util.io.directoryContentOf
import kotlinx.coroutines.runBlocking
import org.editorconfig.Utils
import org.editorconfig.configmanagement.extended.EditorConfigCodeStyleSettingsModifier
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.KotlinLightProjectDescriptor
import org.jetbrains.kotlin.psi.KtFile
import java.nio.file.Files
import java.nio.file.Path

abstract class CodeGenerationTestBase : KotlinLightCodeInsightFixtureTestCase() {
  override fun setUp() {
    super.setUp()
    IntelliJProjectUtil.markAsIntelliJPlatformProject(project, true)
    // Load codegen jar on warm-up phase
    runBlocking {
      CodegenJarLoader.getInstance(project).getClassLoader()
    }
    // KotlinLightCodeInsightFixtureTestCase sets temporary settings
    CodeStyle.dropTemporarySettings(project)
    // Enable .editorconfig in tests
    EditorConfigCodeStyleSettingsModifier.Handler.setEnabledInTests(true)
    Utils.isEnabledInTests = true
  }

  override fun getProjectDescriptor(): LightProjectDescriptor =
    WorkspaceEntitiesProjectDescriptor(shouldAddWorkspaceStorageLibrary, shouldAddWorkspaceJpsEntityLibrary)

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

  private fun onTheFlyReformat(virtualFile: VirtualFile) {
    if (virtualFile.isDirectory) {
      virtualFile.getChildren()?.forEach { onTheFlyReformat(it) }
    }
    else {
      val psiFile = PsiManager.getInstance(project).findFile(virtualFile) as? KtFile? ?: return
      CodeStyleManager.getInstance(project).reformat(psiFile)
    }
  }

  private fun testDirName(suffix: String): String = "${CodeGenerationTestBase::class.java.simpleName}_${testDirectoryName}_${suffix}"

  protected fun generateAndCompare(
    dirWithExpectedApiFiles: Path,
    dirWithExpectedImplFiles: Path,
    pathToPackage: String = ".",
    processAbstractTypes: Boolean,
    explicitApiEnabled: Boolean,
    isTestModule: Boolean,
    formatCode: Boolean
  ) {
    val (srcRoot, genRoot) = generateCode(relativePathToEntitiesDirectory = ".",
                                          processAbstractTypes = processAbstractTypes,
                                          explicitApiEnabled = explicitApiEnabled,
                                          isTestModule = isTestModule,
                                          formatCode = formatCode)

    val srcPackageDir = srcRoot.findFileByRelativePath(pathToPackage) ?: error("Cannot find $pathToPackage under $srcRoot")
    val genPackageDir = genRoot.findFileByRelativePath(pathToPackage) ?: error("Cannot find $pathToPackage under $genRoot")

    val actualApiDirPath = Files.createTempDirectory(testDirName("api_expected"))
    val actualApiDir = VirtualFileManager.getInstance().refreshAndFindFileByNioPath(actualApiDirPath)!!
    if (dirWithExpectedImplFiles.startsWith(dirWithExpectedApiFiles) && dirWithExpectedImplFiles != dirWithExpectedApiFiles) {
      runWriteActionAndWait {
        VfsUtil.copyDirectory(this, srcPackageDir, actualApiDir, null)
      }
      actualApiDirPath.assertMatches(directoryContentOf(dirWithExpectedApiFiles))
    }
    else {
      val actualImplDirPath: Path = Files.createTempDirectory(testDirName("impl_expected"))
      val actualImplDir = VirtualFileManager.getInstance().refreshAndFindFileByNioPath(actualImplDirPath)!!
      runWriteActionAndWait {
        VfsUtil.copyDirectory(this, srcPackageDir, actualApiDir, VirtualFileFilter { it != genRoot })
        VfsUtil.copyDirectory(this, genPackageDir, actualImplDir, null)
      }
      actualApiDirPath.assertMatches(directoryContentOf(dirWithExpectedApiFiles))
      actualImplDirPath.assertMatches(directoryContentOf(dirWithExpectedImplFiles))
    }
  }

  protected fun generateCode(
    relativePathToEntitiesDirectory: String,
    processAbstractTypes: Boolean,
    explicitApiEnabled: Boolean,
    isTestModule: Boolean,
    formatCode: Boolean,
  ): Pair<VirtualFile, VirtualFile> {
    val srcRoot = myFixture.findFileInTempDir(relativePathToEntitiesDirectory)
    val genRoot = myFixture.tempDirFixture.findOrCreateDir("gen/$relativePathToEntitiesDirectory")
    runBlocking {
      CodeWriter.generate(project = project,
                          module = module,
                          srcRoot,
                          processAbstractTypes = processAbstractTypes,
                          explicitApiEnabled = explicitApiEnabled,
                          isTestSourceFolder = false,
                          isTestModule = isTestModule,
                          targetFolderGenerator = { genRoot },
                          existingTargetFolder = { genRoot },
                          formatCode = formatCode)
      FileDocumentManager.getInstance().saveAllDocuments()
    }
    return srcRoot to genRoot
  }

  class WorkspaceEntitiesProjectDescriptor(
    private val addWorkspaceStorageLibrary: Boolean,
    private val addWorkspaceJpsEntityLibrary: Boolean,
  ) : KotlinLightProjectDescriptor() {
    override fun configureModule(module: Module, model: ModifiableRootModel) {
      val contentEntry = model.contentEntries.first()
      val genFolder = VfsUtil.createDirectoryIfMissing(contentEntry.file, "gen")

      contentEntry.addSourceFolder(genFolder,
                                   JavaSourceRootType.SOURCE,
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
