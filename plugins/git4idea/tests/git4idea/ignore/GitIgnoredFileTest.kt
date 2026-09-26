// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.ignore


import com.intellij.configurationStore.saveSettings
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.components.ComponentManagerEx
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.Project.DIRECTORY_STORE_FOLDER
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.NioFiles
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.VcsConfiguration
import com.intellij.openapi.vcs.changes.IgnoredBeanFactory
import com.intellij.openapi.vcs.changes.IgnoredFileDescriptor
import com.intellij.openapi.vcs.changes.IgnoredFileProvider
import com.intellij.openapi.vcs.changes.ignore.psi.util.addNewElements
import com.intellij.openapi.vcs.changes.ignore.psi.util.addNewElementsToIgnoreBlock
import com.intellij.openapi.vcs.changes.ignore.psi.util.updateIgnoreBlock
import com.intellij.openapi.vcs.changes.shelf.ShelveChangesManager
import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.encoding.EncodingProjectManager
import com.intellij.openapi.vfs.newvfs.RefreshQueue
import com.intellij.project.stateStore
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.TimeoutUtil
import com.intellij.util.io.createDirectories
import com.intellij.util.io.createParentDirectories
import com.intellij.vcs.test.refresh
import com.intellij.vcs.test.updateChangeListManager
import com.intellij.vfs.AsyncVfsEventsPostProcessorImpl
import git4idea.GitUtil
import git4idea.GitUtil.DOT_GIT
import git4idea.repo.GitRepositoryFiles.GITIGNORE
import git4idea.test.GitSingleRepoContext
import git4idea.test.file
import git4idea.test.gitSingleRepoContextFixture
import git4idea.test.updateUntrackedFiles
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import java.io.File
import java.nio.file.DirectoryNotEmptyException
import java.nio.file.Path
import kotlin.io.path.createFile
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.readText

const val OUT = "out"
const val EXCLUDED = "excluded"
const val EXCLUDED_CHILD_DIR = "child"
const val EXCLUDED_CHILD = "$EXCLUDED/$EXCLUDED_CHILD_DIR"
const val SHELF = "shelf"


@TestApplication
@RegistryKey(key = "vcs.ignorefile.generation", value = "true")
internal class GitIgnoredFileTest {
  private val fixture = gitSingleRepoContextFixture()
  private val context: GitSingleRepoContext get() = fixture.get()

  @TestDisposable
  lateinit var testDisposable: Disposable

  private lateinit var module: Module

  @BeforeEach
  fun setUp(): Unit = with(context) {
    // will create .idea directory
    runBlockingMaybeCancellable {
      saveSettings(project)
    }
    runInEdtAndWait {
      runWriteAction {
        module = ModuleManager.getInstance(project).newModule("$projectPath/main.iml", "EMPTY_MODULE")
        IndexingTestUtil.waitUntilIndexesAreReady(project)
        projectNioRoot.createDirectories()
        val moduleDir = projectRoot
        module.addContentRoot(moduleDir)
        val outDir = moduleDir.findOrCreateDir(OUT).apply { findOrCreateChildData(this, "out.txt") }
        val excludedDir = moduleDir.findOrCreateDir(EXCLUDED).apply { findOrCreateChildData(this, "excl.txt") }
        val excludedChildDir = excludedDir.findOrCreateDir(EXCLUDED_CHILD_DIR).apply { findOrCreateChildData(this, "excl_child.txt") }

        module.addExclude(outDir)
        module.addExclude(excludedDir)
        module.addExclude(excludedChildDir)
      }
    }
    IndexingTestUtil.waitUntilIndexesAreReady(project)
    AsyncVfsEventsPostProcessorImpl.waitEventsProcessed()

    Disposer.register(testDisposable) { deleteGitDirectoryWithRetry() }
  }

  @Test
  fun `test gitignore content in config dir`(): Unit = with(context) {
    val gitIgnore = file("$DIRECTORY_STORE_FOLDER/$GITIGNORE").create().file.toPath()
    gitIgnore.deleteIfExists()

    val shelf = Path.of(ShelveChangesManager.getShelfPath(project))
    shelf.createDirectories()
    // create file inside shelf dir because we don't add empty (without unversioned files) dirs to gitignore
    shelf.resolve("some.patch").createFile()

    val workspaceFile = project.stateStore.workspacePath
    try {
      workspaceFile.createParentDirectories().createFile()
    }
    catch (_: FileAlreadyExistsException) {
    }
    if (VirtualFileManager.getInstance().refreshAndFindFileByNioPath(workspaceFile) == null) {
      fail("Workspace file doesn't exist and cannot be created")
    }

    generateGitIgnoreAndRefresh(VirtualFileManager.getInstance().refreshAndFindFileByNioPath(project.stateStore.directoryStorePath!!)!!)

    assertGitignoreValid(gitIgnore,
                         """
         # Default ignored files
         /$SHELF/
         /${workspaceFile.fileName}
     """)
  }

  @Test
  fun `test generation default gitignore content in config dir`(): Unit = with(context) {
    val gitIgnore = file("$DIRECTORY_STORE_FOLDER/$GITIGNORE").assertNotExists().file
    val configDir = project.stateStore.directoryStorePath!!
    //TeamCity tests are running against the idea with some plugins enabled, let's imitate it locally to avoid test failures
    addRequiredExtensionsIfNotInstalled(configDir)

    runBlocking {
      GitIgnoreInStoreDirGenerator(project, (project as ComponentManagerEx).getCoroutineScope()).run()
    }

    assertGitignoreValid(gitIgnore,
                         """
        # Default ignored files
        /shelf/
        /workspace.xml
        # Datasource local storage ignored files
        /dataSources/
        /dataSources.local.xml
        # Editor-based HTTP Client requests
        /httpRequests/
     """)
  }

  private fun GitSingleRepoContext.addRequiredExtensionsIfNotInstalled(configDir: Path) {
    registerIgnoredFileProvider(
      "Datasource local storage ignored files",
      setOf(
        IgnoredBeanFactory.ignoreUnderDirectory(configDir.resolve("dataSources").invariantSeparatorsPathString, project),
        IgnoredBeanFactory.ignoreFile(configDir.resolve("dataSources.local.xml").invariantSeparatorsPathString, project),
      ),
    )
    registerIgnoredFileProvider(
      "Editor-based HTTP Client requests",
      setOf(IgnoredBeanFactory.ignoreUnderDirectory(configDir.resolve("httpRequests").invariantSeparatorsPathString,
                                                    project)),
    )
  }

  private fun registerIgnoredFileProvider(description: String, ignoredFiles: Set<IgnoredFileDescriptor>) {
    if (IgnoredFileProvider.IGNORE_FILE.extensionList.any { it.ignoredGroupDescription == description }) return
    IgnoredFileProvider.IGNORE_FILE.point.registerExtension(object : IgnoredFileProvider {
      override fun isIgnoredFile(project: Project, filePath: FilePath): Boolean = false

      override fun getIgnoredFiles(project: Project): Set<IgnoredFileDescriptor> = ignoredFiles

      override fun getIgnoredGroupDescription(): String = description
    }, testDisposable)
  }

  @Test
  fun `test gitignore content in project root`(): Unit = with(context) {
    generateGitIgnoreAndRefresh(projectRoot)

    val gitIgnore = projectNioRoot.resolve(GITIGNORE)

    assertGitignoreValid(gitIgnore,
                         """
        # Project exclude paths
        /$EXCLUDED/
        /$EXCLUDED_CHILD/
        /$OUT/
    """)
  }

  private fun GitSingleRepoContext.generateGitIgnoreAndRefresh(ignoreFileRoot: VirtualFile) {
    GitUtil.generateGitignoreFileIfNeeded(project, ignoreFileRoot)
    refresh()
    VfsUtil.markDirtyAndRefresh(false, true, false, testNioRoot)
    updateChangeListManager()
    updateUntrackedFiles()
  }

  @Test
  fun `test update first ignore block`(): Unit = with(context) {
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

    val ignoreVF = getVirtualFile(gitIgnore)
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

  @Test
  fun `test update middle ignore block`(): Unit = with(context) {
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

    val ignoreVF = getVirtualFile(gitIgnore)
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

  @Test
  fun `test update last ignore block`(): Unit = with(context) {
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

    val ignoreVF = getVirtualFile(gitIgnore)
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

  @Test
  fun `test add elements to first ignore block`(): Unit = with(context) {
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

    val ignoreVF = getVirtualFile(gitIgnore)
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

  @Test
  fun `test add to group to empty ignore file`(): Unit = with(context) {
    val gitIgnore = file(GITIGNORE).create().file
    gitIgnore.writeText("")
    val ignoreVF = getVirtualFile(gitIgnore)
    addNewElementsToIgnoreBlock(project, ignoreVF, "# ignore group",
                                IgnoredBeanFactory.ignoreUnderDirectory("$projectPath/test", project)
    )
    assertGitignoreValid(gitIgnore, """
      # ignore group
      /test/
    """)
  }

  @Test
  fun `test add to empty ignore group`(): Unit = with(context) {
    val gitIgnore = file(GITIGNORE).create().file
    gitIgnore.writeText("# ignore group")
    val ignoreVF = getVirtualFile(gitIgnore)
    addNewElementsToIgnoreBlock(project, ignoreVF, "# ignore group",
                                IgnoredBeanFactory.ignoreUnderDirectory("$projectPath/test", project)
    )
    assertGitignoreValid(gitIgnore, """
      # ignore group
      /test/
    """)
  }

  @Test
  fun `test add to group with remaining last empty group`(): Unit = with(context) {
    val gitIgnore = file(GITIGNORE).create().file
    gitIgnore.writeText("# ignore group\nfoo\n# bar")
    val ignoreVF = getVirtualFile(gitIgnore)
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

  @Test
  fun `test add to empty ignore file`(): Unit = with(context) {
    val gitIgnore = file(GITIGNORE).create().file
    gitIgnore.writeText("")
    val ignoreVF = getVirtualFile(gitIgnore)
    addNewElements(project, ignoreVF, listOf(IgnoredBeanFactory.ignoreUnderDirectory("$projectPath/test", project)))
    assertGitignoreValid(gitIgnore, """
      /test/
    """)
  }


  @Test
  fun `test add to ignore file without trailing newline`(): Unit = with(context) {
    val gitIgnore = file(GITIGNORE).create().file
    gitIgnore.writeText("foo")
    val ignoreVF = getVirtualFile(gitIgnore)
    addNewElements(project, ignoreVF, listOf(IgnoredBeanFactory.ignoreUnderDirectory("$projectPath/test", project)))
    assertGitignoreValid(gitIgnore, """
      foo
      /test/
    """)
  }

  @Test
  fun `test add elements to middle ignore block`(): Unit = with(context) {
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

    val ignoreVF = getVirtualFile(gitIgnore)
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

  @Test
  fun `test add elements to last ignore block`(): Unit = with(context) {
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

    val ignoreVF = getVirtualFile(gitIgnore)
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

  @Test
  fun `test do not add already ignored directories to gitignore`(): Unit = with(context) {
    val shelfDir = WriteAction.computeAndWait<VirtualFile, RuntimeException> {
      val moduleDir = projectRoot
      moduleDir.findOrCreateDir("subdir").findOrCreateDir("shelf")
    }
    val vcsConfiguration = VcsConfiguration.getInstance(project)
    vcsConfiguration.USE_CUSTOM_SHELF_PATH = true
    vcsConfiguration.CUSTOM_SHELF_PATH = shelfDir.path

    assertThat(file(GITIGNORE).create().file.apply {
      writeText("/subdir/shelf")
      RefreshQueue.getInstance().refreshPaths(false, false, null, setOf(this.toPath()))
    }.exists()).isTrue()
    generateGitIgnoreAndRefresh(shelfDir.parent)

    val subdirGitIgnore = File("${shelfDir.parent.path}/$GITIGNORE")

    assertThat(subdirGitIgnore).doesNotExist()
  }

  private fun VirtualFile.findOrCreateDir(dirName: String) = this.findChild(dirName) ?: VfsUtil.createDirectoryIfMissing(this, dirName)

  private fun getVirtualFile(file: File): VirtualFile =
    checkNotNull(StandardFileSystems.local().refreshAndFindFileByPath(file.absolutePath))

  private fun GitSingleRepoContext.deleteGitDirectoryWithRetry() {
    val gitDir = projectNioRoot.resolve(DOT_GIT)
    if (!gitDir.exists()) return

    val dirNotEmptyExceptions = mutableListOf<DirectoryNotEmptyException>()
    do {
      try {
        NioFiles.deleteRecursively(gitDir)
      }
      catch (e: DirectoryNotEmptyException) {
        fileLogger().warn(e)
        dirNotEmptyExceptions.add(e)
        TimeoutUtil.sleep(1000)
        continue
      }

      dirNotEmptyExceptions.clear()
    }
    while (dirNotEmptyExceptions.isNotEmpty())
  }
}

internal fun assertGitignoreValid(ignoreFile: File, gitIgnoreExpectedContent: String) {
  assertGitignoreValid(ignoreFile.toPath(), gitIgnoreExpectedContent)
}

internal fun assertGitignoreValid(ignoreFile: Path, gitIgnoreExpectedContent: String) {
  val gitIgnoreExpectedContentList = gitIgnoreExpectedContent.trimIndent().lines()

  runInEdtAndWait {
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
  }

  assertThat(ignoreFile).exists()
  VirtualFileManager.getInstance().refreshAndFindFileByNioPath(ignoreFile)?.let {
    VfsUtil.markDirtyAndRefresh(false, false, false, it)
  }
  val generatedGitIgnoreContent = ignoreFile.readText()
  assertThat(generatedGitIgnoreContent).describedAs("Generated ignore file is empty").isNotBlank()
  assertThat(generatedGitIgnoreContent).describedAs("Generated ignore file content should be system-independent").doesNotContain("\\")
  assertThat(generatedGitIgnoreContent.lines()).containsAll(gitIgnoreExpectedContentList)
}

internal fun VirtualFile.findOrCreateDir(dirName: String) = this.findChild(dirName) ?: VfsUtil.createDirectoryIfMissing(this, dirName)
