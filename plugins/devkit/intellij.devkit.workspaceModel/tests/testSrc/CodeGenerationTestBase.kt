// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.workspaceModel

import com.intellij.application.options.CodeStyle
import com.intellij.devkit.workspaceModel.codegen.writer.CodeWriter
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.util.JDOMUtil
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
    // Load codegen jar on warm-up phase
    runBlocking {
      CodegenJarLoader.getInstance(project).getClassLoader()
    }
    //set up code style accordingly to settings used in intellij project to ensure that generated code follows it
    val codeStyleSettings = CodeStyle.createTestSettings()
    val state = JDOMUtil.load(Path.of(PathManager.getHomePath(), ".idea", "codeStyles", "Project.xml")).children.first()
    codeStyleSettings.readExternal(state)
    CodeStyle.setTemporarySettings(project, codeStyleSettings)
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

  private fun onTheFlyReformat(virtualFile: VirtualFile) {
    if (virtualFile.isDirectory) {
      virtualFile.getChildren()?.forEach { onTheFlyReformat(it) }
    }
    else {
      val psiFile = PsiManager.getInstance(project).findFile(virtualFile) as? KtFile? ?: error("Cannot find KtFile for ${virtualFile.path}")
      CodeStyleManager.getInstance(project).reformat(psiFile)
    }
  }

  private fun testDirName(suffix: String): String = "${CodeGenerationTestBase::class.java.simpleName}_${testDirectoryName}_${suffix}"

  private fun copyAndReformat(sourceDir: VirtualFile, targetDir: VirtualFile, filter: VirtualFileFilter? = null) {
    WriteCommandAction.runWriteCommandAction(project) {
      VfsUtil.copyDirectory(this, sourceDir, targetDir, filter)
      onTheFlyReformat(targetDir)
      FileDocumentManager.getInstance().saveAllDocuments()
    }
  }

  protected fun generateAndCompare(
    dirWithExpectedApiFiles: Path, dirWithExpectedImplFiles: Path,
    pathToPackage: String = ".",
    processAbstractTypes: Boolean, explicitApiEnabled: Boolean,
    isTestModule: Boolean,
  ) {
    val (srcRoot, genRoot) = generateCode(
      relativePathToEntitiesDirectory = ".",
      processAbstractTypes = processAbstractTypes,
      explicitApiEnabled = explicitApiEnabled,
      isTestModule = isTestModule
    )

    val srcPackageDir = srcRoot.findFileByRelativePath(pathToPackage) ?: error("Cannot find $pathToPackage under $srcRoot")
    val genPackageDir = genRoot.findFileByRelativePath(pathToPackage) ?: error("Cannot find $pathToPackage under $genRoot")

    val vfManager = VirtualFileManager.getInstance()
    val expectedApiDirPath = Files.createTempDirectory(testDirName("api_expected"))
    val actualApiDirPath = Files.createTempDirectory(testDirName("api_actual"))
    if (dirWithExpectedImplFiles.startsWith(dirWithExpectedApiFiles) && dirWithExpectedImplFiles != dirWithExpectedApiFiles) {
      copyAndReformat(srcPackageDir, vfManager.refreshAndFindFileByNioPath(actualApiDirPath)!!)
      copyAndReformat(vfManager.refreshAndFindFileByNioPath(dirWithExpectedApiFiles)!!, vfManager.refreshAndFindFileByNioPath(expectedApiDirPath)!!)
      actualApiDirPath.assertMatches(directoryContentOf(expectedApiDirPath))
    }
    else {
      val expectedImplDirPath = Files.createTempDirectory(testDirName("impl_expected"))
      val actualImplDirPath = Files.createTempDirectory(testDirName("impl_actual"))
      copyAndReformat(srcPackageDir, vfManager.refreshAndFindFileByNioPath(actualApiDirPath)!!, VirtualFileFilter { it != genRoot })
      copyAndReformat(vfManager.refreshAndFindFileByNioPath(dirWithExpectedApiFiles)!!, vfManager.refreshAndFindFileByNioPath(expectedApiDirPath)!!, VirtualFileFilter { it != genRoot })
      copyAndReformat(genPackageDir, vfManager.refreshAndFindFileByNioPath(actualImplDirPath)!!)
      copyAndReformat(vfManager.refreshAndFindFileByNioPath(dirWithExpectedImplFiles)!!, vfManager.refreshAndFindFileByNioPath(expectedImplDirPath)!!)
      actualApiDirPath.assertMatches(directoryContentOf(expectedApiDirPath))
      actualImplDirPath.assertMatches(directoryContentOf(expectedImplDirPath))
    }
  }

  protected fun generateCode(
    relativePathToEntitiesDirectory: String, processAbstractTypes: Boolean, explicitApiEnabled: Boolean, isTestModule: Boolean,
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
    private val addWorkspaceJpsEntityLibrary: Boolean,
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
