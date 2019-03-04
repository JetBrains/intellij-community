// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.ignore

import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.project.Project.DIRECTORY_STORE_FOLDER
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.VcsConfiguration
import com.intellij.openapi.vcs.changes.shelf.ShelveChangesManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.encoding.EncodingProjectManager
import com.intellij.project.stateStore
import git4idea.GitUtil
import git4idea.repo.GitRepositoryFiles.GITIGNORE
import git4idea.test.GitPlatformTest
import git4idea.test.createRepository
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths

const val OUT = "out"
const val EXCLUDED = "excluded"
const val EXCLUDED_CHILD_DIR = "child"
const val EXCLUDED_CHILD = "$EXCLUDED/$EXCLUDED_CHILD_DIR"
const val SHELF = "shelf"

class GitIgnoredFileTest : GitPlatformTest() {

  override fun getProjectDirOrFile(): Path {
    val projectRoot = File(testRoot, "project")
    val file: File = FileUtil.createTempDirectory(projectRoot, FileUtil.sanitizeFileName(name, true), "")
    val ideaDir = file.resolve(DIRECTORY_STORE_FOLDER)
    ideaDir.mkdir()
    return file.toPath()
  }

  override fun setUp() {
    super.setUp()
    Registry.get("vcs.ignorefile.generation").setValue(true, testRootDisposable)
    createRepository(project, projectPath)
  }

  override fun setUpModule() {
    WriteAction.runAndWait<RuntimeException> {
      myModule = createMainModule()
      val moduleDir = myModule.moduleFile!!.parent
      myModule.addContentRoot(moduleDir)
      val outDir = moduleDir.findOrCreateDir(OUT)
      val excludedDir = moduleDir.findOrCreateDir(EXCLUDED)
      val excludedChildDir = excludedDir.findOrCreateDir(EXCLUDED_CHILD_DIR)
      myModule.addExclude(outDir)
      myModule.addExclude(excludedDir)
      myModule.addExclude(excludedChildDir)
    }
  }

  fun `test gitignore content in config dir`() {
    val gitIgnore = File("$projectPath/$DIRECTORY_STORE_FOLDER/$GITIGNORE")
    if (gitIgnore.exists()) gitIgnore.delete()

    val shelf = File(ShelveChangesManager.getShelfPath(project))
    val shelfExist = if (shelf.exists()) true else shelf.mkdir()
    if (!shelfExist) fail("Shelf doesn't exist and cannot be created")

    val workspaceFilePath = project.stateStore.workspaceFilePath
    if (workspaceFilePath == null) fail("Cannot detect workspace file path")
    val workspaceFile = File(workspaceFilePath!!)
    val workspaceFileExist = FileUtil.createIfNotExists(workspaceFile)
    if (!workspaceFileExist || VfsUtil.findFileByIoFile(workspaceFile, true) == null) fail("Workspace file doesn't exist and cannot be created")

    GitUtil.generateGitignoreFileIfNeeded(project, VfsUtil.findFile(Paths.get("$projectPath/$DIRECTORY_STORE_FOLDER"), true)!!)

    assertGitignoreValid(gitIgnore,
                         """
        # Default ignored files
        /$SHELF/
        /${workspaceFile.name}
    """)
  }

  fun `test gitignore content in project root`() {
    GitUtil.generateGitignoreFileIfNeeded(project, projectRoot)

    val gitIgnore = File("$projectPath/$GITIGNORE")

    assertGitignoreValid(gitIgnore,
                         """
        # Project exclude paths
        /$EXCLUDED/
        /$EXCLUDED_CHILD/
        /$OUT/
    """)
  }

  fun `test do not add already ignored directories to gitignore`() {
    val shelfDir = WriteAction.computeAndWait<VirtualFile, RuntimeException> {
      val moduleDir = myModule.moduleFile!!.parent
      moduleDir.findOrCreateDir("subdir").findOrCreateDir("shelf")
    }
    val vcsConfiguration = VcsConfiguration.getInstance(project)
    vcsConfiguration.USE_CUSTOM_SHELF_PATH = true
    vcsConfiguration.CUSTOM_SHELF_PATH = shelfDir.path

    assertTrue(File("$projectPath/$GITIGNORE").apply {
      writeText("/subdir/shelf")
      LocalFileSystem.getInstance().refreshIoFiles(setOf(this))
    }.exists())

    GitUtil.generateGitignoreFileIfNeeded(project, shelfDir.parent)

    val subdirGitIgnore = File("${shelfDir.parent.path}/$GITIGNORE")

    assertFalse(subdirGitIgnore.exists())
  }

  private fun assertGitignoreValid(ignoreFile: File, gitIgnoreExpectedContent: String) {
    val projectCharset = EncodingProjectManager.getInstance(project).defaultCharset
    val gitIgnoreExpectedContentList = gitIgnoreExpectedContent.trimIndent().lines()

    assertTrue(ignoreFile.exists())
    val generatedGitIgnoreContent = ignoreFile.readText(projectCharset)
    assertFalse("Generated ignore file is empty", generatedGitIgnoreContent.isBlank())
    assertFalse("Generated ignore file content should be system-independent", generatedGitIgnoreContent.contains('\\'))
    assertContainsOrdered(generatedGitIgnoreContent.lines(), gitIgnoreExpectedContentList)
  }

  private fun VirtualFile.findOrCreateDir(dirName: String) = this.findChild(dirName) ?: createChildDirectory(this, dirName)
}