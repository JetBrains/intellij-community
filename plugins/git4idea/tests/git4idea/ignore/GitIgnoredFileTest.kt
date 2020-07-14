// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.intellij.openapi.vcs.changes.ignore.psi.util.addNewElements
import com.intellij.openapi.vcs.changes.ignore.psi.util.addNewElementsToIgnoreBlock
import com.intellij.openapi.vcs.changes.ignore.psi.util.updateIgnoreBlock
import com.intellij.openapi.vcs.changes.shelf.ShelveChangesManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.encoding.EncodingProjectManager
import com.intellij.project.stateStore
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.io.createFile
import git4idea.GitUtil
import git4idea.repo.GitRepositoryFiles.GITIGNORE
import git4idea.test.GitSingleRepoTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.Assert.assertFalse
import java.io.File

const val OUT = "out"
const val EXCLUDED = "excluded"
const val EXCLUDED_CHILD_DIR = "child"
const val EXCLUDED_CHILD = "$EXCLUDED/$EXCLUDED_CHILD_DIR"
const val SHELF = "shelf"

class GitIgnoredFileTest : GitSingleRepoTest() {

  override fun isCreateDirectoryBasedProject() = true

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
      val moduleDir = getOrCreateProjectBaseDir()
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
    val gitIgnore = file("$DIRECTORY_STORE_FOLDER/$GITIGNORE").create().file
    if (gitIgnore.exists()) gitIgnore.delete()

    val shelf = File(ShelveChangesManager.getShelfPath(project))
    val shelfExist = if (shelf.exists()) true else shelf.mkdir()
    if (!shelfExist) {
      fail("Shelf doesn't exist and cannot be created")
    }
    // create file inside shelf dir because we don't add empty (without unversioned files) dirs to gitignore
    FileUtil.createIfDoesntExist(File(shelf, "some.patch"))

    val workspaceFile = project.stateStore.workspacePath
    val workspaceFileExist = try {
      workspaceFile.createFile()
      true
    }
    catch (e: FileAlreadyExistsException) {
      true
    }
    if (!workspaceFileExist || LocalFileSystem.getInstance().refreshAndFindFileByNioFile(workspaceFile) == null) {
      fail("Workspace file doesn't exist and cannot be created")
    }

    GitUtil.generateGitignoreFileIfNeeded(project, LocalFileSystem.getInstance().refreshAndFindFileByNioFile(project.stateStore.directoryStorePath!!)!!)

    assertGitignoreValid(gitIgnore,
                         """
         # Default ignored files
         /$SHELF/
         /${workspaceFile.fileName}
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
    val gitIgnore = file(GITIGNORE).create().file
    gitIgnore.writeText(
      """
    $firstBlock

    $middleBlock

    $lastBlock
    """.trimIndent(), projectCharset
    )

    val ignoreVF = getVirtualFile(gitIgnore) ?: return
    val ignoreGroup = "# first block"
    updateIgnoreBlock(project, ignoreVF, ignoreGroup,
                                      IgnoredBeanFactory.ignoreUnderDirectory("$projectPath/test", project),
                                      IgnoredBeanFactory.ignoreFile("$projectPath/file.txt", project))

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
    val gitIgnore = file(GITIGNORE).create().file
    gitIgnore.writeText(
      """
    $firstBlock

    $middleBlock

    $lastBlock
    """.trimIndent(), projectCharset
    )

    val ignoreVF = getVirtualFile(gitIgnore) ?: return
    val ignoreGroup = "# middle block"
    updateIgnoreBlock(project, ignoreVF, ignoreGroup,
                                      IgnoredBeanFactory.ignoreUnderDirectory("$projectPath/test", project),
                                      IgnoredBeanFactory.ignoreFile("$projectPath/file.txt", project))

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
    val gitIgnore = file(GITIGNORE).create().file
    gitIgnore.writeText(
      """
    $firstBlock

    $middleBlock

    $lastBlock
    """.trimIndent(), projectCharset
    )

    val ignoreVF = getVirtualFile(gitIgnore) ?: return
    val ignoreGroup = "# last block"
    updateIgnoreBlock(project, ignoreVF, ignoreGroup,
                                      IgnoredBeanFactory.ignoreUnderDirectory("$projectPath/test", project),
                                      IgnoredBeanFactory.ignoreFile("$projectPath/file.txt", project))

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
    addNewElementsToIgnoreBlock(project, ignoreVF, ignoreGroup,
                              IgnoredBeanFactory.ignoreUnderDirectory("$projectPath/test", project),
                              IgnoredBeanFactory.ignoreFile("$projectPath/file.txt", project),
                              IgnoredBeanFactory.ignoreFile("$projectPath/file2.txt", project),
                              IgnoredBeanFactory.ignoreFile("$projectPath/file3.txt", project),
                              IgnoredBeanFactory.ignoreUnderDirectory("$projectPath/$EXCLUDED_CHILD/", project),
                              IgnoredBeanFactory.ignoreUnderDirectory("$projectPath/$EXCLUDED", project))

    assertGitignoreValid(gitIgnore, """
    $newFirstBlock

    $middleBlock

    $lastBlock
    """)
  }

  fun `test add to group to empty ignore file`() {
    val gitIgnore = file(GITIGNORE).create().file
    gitIgnore.writeText("")
    val ignoreVF = getVirtualFile(gitIgnore) ?: return
    addNewElementsToIgnoreBlock(project, ignoreVF, "# ignore group",
                                IgnoredBeanFactory.ignoreUnderDirectory("$projectPath/test", project)
    )
    assertGitignoreValid(gitIgnore, """
      # ignore group
      /test/
    """)
  }

  fun `test add to empty ignore group`() {
    val gitIgnore = file(GITIGNORE).create().file
    gitIgnore.writeText("# ignore group")
    val ignoreVF = getVirtualFile(gitIgnore) ?: return
    addNewElementsToIgnoreBlock(project, ignoreVF, "# ignore group",
                                IgnoredBeanFactory.ignoreUnderDirectory("$projectPath/test", project)
    )
    assertGitignoreValid(gitIgnore, """
      # ignore group
      /test/
    """)
  }

  fun `test add to group with remaining last empty group`() {
    val gitIgnore = file(GITIGNORE).create().file
    gitIgnore.writeText("# ignore group\nfoo\n# bar")
    val ignoreVF = getVirtualFile(gitIgnore) ?: return
    addNewElementsToIgnoreBlock(project, ignoreVF, "# ignore group",
                                IgnoredBeanFactory.ignoreUnderDirectory("$projectPath/test", project)
    )
    assertGitignoreValid(gitIgnore, """
      # ignore group
      foo
      /test/
      # bar
    """)
  }

  fun `test add to empty ignore file`() {
    val gitIgnore = file(GITIGNORE).create().file
    gitIgnore.writeText("")
    val ignoreVF = getVirtualFile(gitIgnore) ?: return
    addNewElements(project, ignoreVF, listOf(IgnoredBeanFactory.ignoreUnderDirectory("$projectPath/test", project)))
    assertGitignoreValid(gitIgnore, """
      /test/
    """)
  }


  fun `test add to ignore file without trailing newline`() {
    val gitIgnore = file(GITIGNORE).create().file
    gitIgnore.writeText("foo")
    val ignoreVF = getVirtualFile(gitIgnore) ?: return
    addNewElements(project, ignoreVF, listOf(IgnoredBeanFactory.ignoreUnderDirectory("$projectPath/test", project)))
    assertGitignoreValid(gitIgnore, """
      foo
      /test/
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
    val gitIgnore = file(GITIGNORE).create().file
    gitIgnore.writeText(
      """
    $firstBlock

    $middleBlock

    $lastBlock
    """.trimIndent(), projectCharset
    )

    val ignoreVF = getVirtualFile(gitIgnore) ?: return
    val ignoreGroup = "# middle block"
    addNewElementsToIgnoreBlock(project, ignoreVF, ignoreGroup,
                                                IgnoredBeanFactory.ignoreUnderDirectory("$projectPath/test", project),
                                                IgnoredBeanFactory.ignoreFile("$projectPath/file.txt", project),
                                                IgnoredBeanFactory.ignoreFile("$projectPath/file2.txt", project),
                                                IgnoredBeanFactory.ignoreFile("$projectPath/file3.txt", project),
                                                IgnoredBeanFactory.ignoreUnderDirectory("$projectPath/$EXCLUDED_CHILD", project),
                                                IgnoredBeanFactory.ignoreUnderDirectory("$projectPath/$EXCLUDED", project))

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
    val gitIgnore = file(GITIGNORE).create().file
    gitIgnore.writeText(
      """
    $firstBlock

    $middleBlock

    $lastBlock
    """.trimIndent(), projectCharset
    )

    val ignoreVF = getVirtualFile(gitIgnore) ?: return
    val ignoreGroup = "# last block"
    addNewElementsToIgnoreBlock(project, ignoreVF, ignoreGroup,
                                                IgnoredBeanFactory.ignoreUnderDirectory("$projectPath/test", project),
                                                IgnoredBeanFactory.ignoreFile("$projectPath/file.txt", project),
                                                IgnoredBeanFactory.ignoreFile("$projectPath/file2.txt", project),
                                                IgnoredBeanFactory.ignoreFile("$projectPath/file3.txt", project),
                                                IgnoredBeanFactory.ignoreFile("$projectPath/file4.txt", project))

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

    assertTrue(file(GITIGNORE).create().file.apply {
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

  runInEdtAndWait {
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
  }

  assertThat(ignoreFile).exists()
  val generatedGitIgnoreContent = ignoreFile.readText()
  assertFalse("Generated ignore file is empty", generatedGitIgnoreContent.isBlank())
  assertFalse("Generated ignore file content should be system-independent", generatedGitIgnoreContent.contains('\\'))
  UsefulTestCase.assertContainsOrdered(generatedGitIgnoreContent.lines(), gitIgnoreExpectedContentList)
}

internal fun VirtualFile.findOrCreateDir(dirName: String) = this.findChild(dirName) ?: createChildDirectory(this, dirName)