// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.ignore

import com.intellij.configurationStore.saveComponentManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.project.Project.DIRECTORY_STORE_FOLDER
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.VcsConfiguration
import com.intellij.openapi.vcs.changes.IgnoredBeanFactory
import com.intellij.openapi.vcs.changes.ignore.psi.util.addNewElementsToIgnoreBlock
import com.intellij.openapi.vcs.changes.ignore.psi.util.updateIgnoreBlock
import com.intellij.openapi.vcs.changes.shelf.ShelveChangesManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.encoding.EncodingProjectManager
import com.intellij.project.stateStore
import com.intellij.testFramework.PlatformTestCase
import com.intellij.testFramework.UsefulTestCase
import git4idea.GitUtil
import git4idea.repo.GitRepositoryFiles.GITIGNORE
import git4idea.test.GitSingleRepoTest
import java.io.File
import java.nio.file.Paths

const val OUT = "out"
const val EXCLUDED = "excluded"
const val EXCLUDED_CHILD_DIR = "child"
const val EXCLUDED_CHILD = "$EXCLUDED/$EXCLUDED_CHILD_DIR"
const val SHELF = "shelf"

class GitIgnoredFileTest : GitSingleRepoTest() {

  override fun getProjectDirOrFile() = getProjectDirOrFile(true)

  override fun setUp() {
    super.setUp()
    Registry.get("vcs.ignorefile.generation").setValue(true, testRootDisposable)
  }

  override fun setUpProject() {
    super.setUpProject()
    invokeAndWaitIfNeeded { saveComponentManager(project) } //will create .idea directory
  }

  override fun setUpModule() {
    runWriteAction {
      myModule = createMainModule()
      val moduleDir = myModule.moduleFile!!.parent
      myModule.addContentRoot(moduleDir)
      val outDir = moduleDir.findOrCreateDir(OUT).apply { findOrCreateChildData(this, "out.txt") }
      val excludedDir = moduleDir.findOrCreateDir(EXCLUDED).apply { findOrCreateChildData(this, "excl.txt") }
      val excludedChildDir = excludedDir.findOrCreateDir(EXCLUDED_CHILD_DIR).apply { findOrCreateChildData(this, "excl_child.txt") }
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
    FileUtil.createIfDoesntExist(File(shelf, "some.patch")) //create file inside shelf dir because we don't add empty (without unversioned files) dirs to gitignore

    val workspaceFilePath = project.stateStore.workspaceFilePath
    if (workspaceFilePath == null) fail("Cannot detect workspace file path")
    val workspaceFile = File(workspaceFilePath!!)
    val workspaceFileExist = FileUtil.createIfNotExists(workspaceFile)
    if (!workspaceFileExist || VfsUtil.findFileByIoFile(workspaceFile, true) == null)
      fail("Workspace file doesn't exist and cannot be created")

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

  fun `test update first ignore block`() {
    val projectCharset = EncodingProjectManager.getInstance(project).defaultCharset
    val firstBlock = """
      # first block
      /$EXCLUDED/
      /$EXCLUDED_CHILD/
      /$OUT/
    """
    val middleBlock = """
      # middle block
      /middleBlockFolder/
      /generatedMiddle/
      /folder/*.txt
      *.xml
    """
    val lastBlock = """
      # last block
      /testInBlock2/
      /generated/
      *.txt
    """
    val newFirstBlock = """
      # first block
      /test/
      /file.txt
    """
    val gitIgnore = File("$projectPath/$GITIGNORE")
    gitIgnore.writeText(
      """
    $firstBlock

    $middleBlock

    $lastBlock
    """.trimIndent(), projectCharset
    )

    val ignoreVF = getVirtualFile(gitIgnore) ?: return
    val ignoreGroup = "# first block"
    val psiIgnore = updateIgnoreBlock(project, ignoreVF, ignoreGroup,
                                      IgnoredBeanFactory.ignoreUnderDirectory("$projectPath/test", project),
                                      IgnoredBeanFactory.ignoreFile("$projectPath/file.txt", project))
    gitIgnore.writeText(psiIgnore?.text ?: "", projectCharset)

    assertGitignoreValid(gitIgnore, """
    $newFirstBlock

    $middleBlock

    $lastBlock
    """)
  }

  fun `test update middle ignore block`() {
    val projectCharset = EncodingProjectManager.getInstance(project).defaultCharset
    val firstBlock = """
      # first block
      /$EXCLUDED/
      /$EXCLUDED_CHILD/
      /$OUT/
    """
    val middleBlock = """
      # middle block
      /middleBlockFolder/
      /generatedMiddle/
      /folder/*.txt
      *.xml
    """
    val lastBlock = """
      # last block
      /testInBlock2/
      /generated/
      *.txt
    """
    val newMiddleBlock = """
      # middle block
      /test/
      /file.txt
    """
    val gitIgnore = File("$projectPath/$GITIGNORE")
    gitIgnore.writeText(
      """
    $firstBlock

    $middleBlock

    $lastBlock
    """.trimIndent(), projectCharset
    )

    val ignoreVF = getVirtualFile(gitIgnore) ?: return
    val ignoreGroup = "# middle block"
    val psiIgnore = updateIgnoreBlock(project, ignoreVF, ignoreGroup,
                                      IgnoredBeanFactory.ignoreUnderDirectory("$projectPath/test", project),
                                      IgnoredBeanFactory.ignoreFile("$projectPath/file.txt", project))
    gitIgnore.writeText(psiIgnore?.text ?: "", projectCharset)

    assertGitignoreValid(gitIgnore, """
    $firstBlock

    $newMiddleBlock

    $lastBlock
    """)
  }

  fun `test update last ignore block`() {
    val projectCharset = EncodingProjectManager.getInstance(project).defaultCharset
    val firstBlock = """
      # first block
      /$EXCLUDED/
      /$EXCLUDED_CHILD/
      /$OUT/
    """
    val middleBlock = """
      # middle block
      /middleBlockFolder/
      /generatedMiddle/
      /folder/*.txt
      *.xml
    """
    val lastBlock = """
      # last block
      /testInBlock2/
      /generated/
      *.txt
    """
    val newLastBlock = """
      # last block
      /test/
      /file.txt
    """
    val gitIgnore = File("$projectPath/$GITIGNORE")
    gitIgnore.writeText(
      """
    $firstBlock

    $middleBlock

    $lastBlock
    """.trimIndent(), projectCharset
    )

    val ignoreVF = getVirtualFile(gitIgnore) ?: return
    val ignoreGroup = "# last block"
    val psiIgnore = updateIgnoreBlock(project, ignoreVF, ignoreGroup,
                                      IgnoredBeanFactory.ignoreUnderDirectory("$projectPath/test", project),
                                      IgnoredBeanFactory.ignoreFile("$projectPath/file.txt", project))
    gitIgnore.writeText(psiIgnore?.text ?: "", projectCharset)

    assertGitignoreValid(gitIgnore, """
    $firstBlock

    $middleBlock

    $newLastBlock
    """)
  }

  fun `test add elements to first ignore block`() {
    val projectCharset = EncodingProjectManager.getInstance(project).defaultCharset
    val firstBlock = """
      # first block
      /$EXCLUDED/
      /$EXCLUDED_CHILD/
      /$OUT/
    """
    val middleBlock = """
      # middle block
      /middleBlockFolder/
      /generatedMiddle/
      /folder/*.txt
      *.xml
    """
    val lastBlock = """
      # last block
      /testInBlock2/
      /generated/
      *.txt
    """
    val newFirstBlock = """
      # first block
      /$EXCLUDED/
      /$EXCLUDED_CHILD/
      /$OUT/
      /test/
      /file.txt
      /file2.txt
      /file3.txt
    """
    val gitIgnore = File("$projectPath/$GITIGNORE")
    gitIgnore.writeText(
      """
    $firstBlock

    $middleBlock

    $lastBlock
    """.trimIndent(), projectCharset
    )

    val ignoreVF = getVirtualFile(gitIgnore) ?: return
    val ignoreGroup = "# first block"
    val psiIgnore = addNewElementsToIgnoreBlock(project, ignoreVF, ignoreGroup,
                                                IgnoredBeanFactory.ignoreUnderDirectory("$projectPath/test", project),
                                                IgnoredBeanFactory.ignoreFile("$projectPath/file.txt", project),
                                                IgnoredBeanFactory.ignoreFile("$projectPath/file2.txt", project),
                                                IgnoredBeanFactory.ignoreFile("$projectPath/file3.txt", project),
                                                IgnoredBeanFactory.ignoreUnderDirectory("$projectPath/$EXCLUDED_CHILD/", project),
                                                IgnoredBeanFactory.ignoreUnderDirectory("$projectPath/$EXCLUDED", project))
    gitIgnore.writeText(psiIgnore?.text ?: "", projectCharset)

    assertGitignoreValid(gitIgnore, """
    $newFirstBlock

    $middleBlock

    $lastBlock
    """)
  }

  fun `test add elements to middle ignore block`() {
    val projectCharset = EncodingProjectManager.getInstance(project).defaultCharset
    val firstBlock = """
      # first block
      /$EXCLUDED/
      /$EXCLUDED_CHILD/
      /$OUT/
    """
    val middleBlock = """
      # middle block
      /middleBlockFolder/
      /generatedMiddle/
      /folder/*.txt
      *.xml
    """
    val lastBlock = """
      # last block
      /testInBlock2/
      /generated/
      *.txt
    """
    val newMiddleBlock = """
      # middle block
      /middleBlockFolder/
      /generatedMiddle/
      /folder/*.txt
      *.xml
      /test/
      /file.txt
      /file2.txt
      /file3.txt
      /$EXCLUDED_CHILD/
      /$EXCLUDED/
    """
    val gitIgnore = File("$projectPath/$GITIGNORE")
    gitIgnore.writeText(
      """
    $firstBlock

    $middleBlock

    $lastBlock
    """.trimIndent(), projectCharset
    )

    val ignoreVF = getVirtualFile(gitIgnore) ?: return
    val ignoreGroup = "# middle block"
    val psiIgnore = addNewElementsToIgnoreBlock(project, ignoreVF, ignoreGroup,
                                                IgnoredBeanFactory.ignoreUnderDirectory("$projectPath/test", project),
                                                IgnoredBeanFactory.ignoreFile("$projectPath/file.txt", project),
                                                IgnoredBeanFactory.ignoreFile("$projectPath/file2.txt", project),
                                                IgnoredBeanFactory.ignoreFile("$projectPath/file3.txt", project),
                                                IgnoredBeanFactory.ignoreUnderDirectory("$projectPath/$EXCLUDED_CHILD", project),
                                                IgnoredBeanFactory.ignoreUnderDirectory("$projectPath/$EXCLUDED", project))
    gitIgnore.writeText(psiIgnore?.text ?: "", projectCharset)

    assertGitignoreValid(gitIgnore, """
    $firstBlock

    $newMiddleBlock

    $lastBlock
    """)
  }

  fun `test add elements to last ignore block`() {
    val projectCharset = EncodingProjectManager.getInstance(project).defaultCharset
    val firstBlock = """
      # first block
      /$EXCLUDED/
      /$EXCLUDED_CHILD/
      /$OUT/
    """
    val middleBlock = """
      # middle block
      /middleBlockFolder/
      /generatedMiddle/
      /folder/*.txt
      *.xml
    """
    val lastBlock = """
      # last block
      /testInBlock2/
      /generated/
      *.txt
    """
    val newLastBlock = """
      # last block
      /testInBlock2/
      /generated/
      *.txt
      /test/
      /file.txt
      /file2.txt
      /file3.txt
      /file4.txt
    """
    val gitIgnore = File("$projectPath/$GITIGNORE")
    gitIgnore.writeText(
      """
    $firstBlock

    $middleBlock

    $lastBlock
    """.trimIndent(), projectCharset
    )

    val ignoreVF = getVirtualFile(gitIgnore) ?: return
    val ignoreGroup = "# last block"
    val psiIgnore = addNewElementsToIgnoreBlock(project, ignoreVF, ignoreGroup,
                                                IgnoredBeanFactory.ignoreUnderDirectory("$projectPath/test", project),
                                                IgnoredBeanFactory.ignoreFile("$projectPath/file.txt", project),
                                                IgnoredBeanFactory.ignoreFile("$projectPath/file2.txt", project),
                                                IgnoredBeanFactory.ignoreFile("$projectPath/file3.txt", project),
                                                IgnoredBeanFactory.ignoreFile("$projectPath/file4.txt", project))
    gitIgnore.writeText(psiIgnore?.text ?: "", projectCharset)

    assertGitignoreValid(gitIgnore, """
    $firstBlock

    $middleBlock

    $newLastBlock
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

  private fun VirtualFile.findOrCreateDir(dirName: String) = this.findChild(dirName) ?: createChildDirectory(this, dirName)
}

internal fun assertGitignoreValid(ignoreFile: File, gitIgnoreExpectedContent: String) {
  val gitIgnoreExpectedContentList = gitIgnoreExpectedContent.trimIndent().lines()

  UsefulTestCase.assertExists(ignoreFile)
  val generatedGitIgnoreContent = ignoreFile.readText()
  PlatformTestCase.assertFalse("Generated ignore file is empty", generatedGitIgnoreContent.isBlank())
  PlatformTestCase.assertFalse("Generated ignore file content should be system-independent", generatedGitIgnoreContent.contains('\\'))
  PlatformTestCase.assertContainsOrdered(generatedGitIgnoreContent.lines(), gitIgnoreExpectedContentList)
}

internal fun VirtualFile.findOrCreateDir(dirName: String) = this.findChild(dirName) ?: createChildDirectory(this, dirName)