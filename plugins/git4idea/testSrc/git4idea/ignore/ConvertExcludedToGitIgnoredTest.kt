// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.ignore

import com.intellij.configurationStore.saveSettings
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.module.EmptyModuleType
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.ModuleType
import com.intellij.openapi.roots.CompilerProjectExtension
import com.intellij.openapi.vcs.VcsApplicationSettings
import com.intellij.openapi.vcs.changes.VcsIgnoreManagerImpl
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.project.stateStore
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.VfsTestUtil.createDir
import com.intellij.testFramework.VfsTestUtil.createFile
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import com.intellij.vcsUtil.VcsImplUtil
import com.intellij.vfs.AsyncVfsEventsPostProcessorImpl
import git4idea.repo.GitRepositoryFiles.GITIGNORE
import git4idea.test.GitSingleRepoContext
import git4idea.test.gitSingleRepoContextFixture
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Path
import java.util.concurrent.TimeUnit

@TestApplication
@RegistryKey(key = "vcs.ignorefile.generation", value = "true")
@RegistryKey(key = "ide.hide.excluded.files", value = "false")
class ConvertExcludedToGitIgnoredTest {
  private val fixture = gitSingleRepoContextFixture()
  private val context: GitSingleRepoContext get() = fixture.get()

  private val moduleFixture = fixture.projectModuleFixture()
  private val module: Module get() = moduleFixture.get()
  private val moduleContentRoot: VirtualFile get() = context.projectRoot
  private val gitIgnore: Path get() = context.projectNioRoot.resolve(GITIGNORE)

  @BeforeEach
  fun setUp() {
    VcsApplicationSettings.getInstance().MANAGE_IGNORE_FILES = true
  }

  @Test
  fun testExcludedFolder(): Unit = contextTest {
    val excluded = createDir(moduleContentRoot, "exc")
    createFile(excluded, "excluded.txt") //Don't mark empty directories like ignored since versioning such directories not supported in Git
    PsiTestUtil.addExcludedRoot(module, excluded)

    generateIgnoreFileAndWaitHoldersUpdate()
    assertGitignoreValid(gitIgnore, """
    # Project exclude paths
    /exc/
    """)

    assertFalse(changeListManager.isIgnoredFile(moduleContentRoot))
    assertTrue(changeListManager.isIgnoredFile(excluded))
  }

  @Test
  fun testModuleOutput(): Unit = contextTest {
    val output = createDir(moduleContentRoot, "out")
    PsiTestUtil.setCompilerOutputPath(module, output.url, false)
    createFile(output, "out.class")

    generateIgnoreFileAndWaitHoldersUpdate()
    assertGitignoreValid(gitIgnore, """
    # Project exclude paths
    /out/
    """)

    assertFalse(changeListManager.isIgnoredFile(moduleContentRoot))
    assertTrue(changeListManager.isIgnoredFile(output))
  }

  @Test
  fun testProjectOutput(): Unit = contextTest {
    val output = createDir(projectRoot, "projectOutput")
    createFile(output, "out.class")
    CompilerProjectExtension.getInstance(project)!!.apply {
      edtWriteAction {
        compilerOutputUrl = output.url
      }
    }

    generateIgnoreFileAndWaitHoldersUpdate()
    assertGitignoreValid(gitIgnore, """
    # Project exclude paths
    /projectOutput/
    """)

    assertTrue(changeListManager.isIgnoredFile(output))
  }

  @Test
  fun testModuleOutputUnderProjectOutput(): Unit = contextTest {
    val output = createDir(projectRoot, "projectOutput")
    createFile(output, "out.class")
    CompilerProjectExtension.getInstance(project)!!.apply {
      edtWriteAction {
        compilerOutputUrl = output.url
      }
    }
    val moduleOutput = createDir(output, "module")
    PsiTestUtil.setCompilerOutputPath(module, moduleOutput.url, false)

    generateIgnoreFileAndWaitHoldersUpdate()
    assertGitignoreValid(gitIgnore, """
    # Project exclude paths
    /projectOutput/
    """)

    assertTrue(changeListManager.isIgnoredFile(output))
    assertTrue(changeListManager.isIgnoredFile(moduleOutput))
  }

  @Test
  fun testModuleOutputUnderExcluded(): Unit = contextTest {
    val excluded = createDir(moduleContentRoot, "target")
    createFile(excluded, "out.class")
    PsiTestUtil.addExcludedRoot(module, excluded)
    val moduleOutput = createDir(excluded, "classes")
    createFile(moduleOutput, "out.class")
    PsiTestUtil.setCompilerOutputPath(module, moduleOutput.url, false)

    generateIgnoreFileAndWaitHoldersUpdate()
    assertGitignoreValid(gitIgnore, """
    # Project exclude paths
    /target/
    """)

    assertTrue(changeListManager.isIgnoredFile(excluded))
    assertTrue(changeListManager.isIgnoredFile(moduleOutput))
  }

  @Test
  fun testDoNotIgnoreInnerModuleExplicitlyMarkedAsExcludedFromOuterModule(): Unit = with(context) {
    val inner = createDir(moduleContentRoot, "inner")
    createFile(inner, "inner.txt")
    PsiTestUtil.addModule(project, ModuleType.EMPTY, "inner", inner)
    PsiTestUtil.addExcludedRoot(module, inner)

    assertFalse(changeListManager.isIgnoredFile(inner))
  }

  private fun contextTest(test: suspend GitSingleRepoContext.() -> Unit): Unit = runBlocking {
    context.test()
  }

  private suspend fun GitSingleRepoContext.generateIgnoreFileAndWaitHoldersUpdate() {
    AsyncVfsEventsPostProcessorImpl.waitEventsProcessed()
    flushIgnoreHoldersQueue()
    VcsImplUtil.generateIgnoreFileIfNeeded(project, vcs, projectRoot)
    AsyncVfsEventsPostProcessorImpl.waitEventsProcessed()
    repo.untrackedFilesHolder.invalidate()
    repo.untrackedFilesHolder.awaitNotBusy()
  }

  private fun GitSingleRepoContext.flushIgnoreHoldersQueue() {
    VcsIgnoreManagerImpl.getInstanceImpl(project).ignoreRefreshQueue.waitForAllExecuted(10, TimeUnit.MINUTES)
  }
}

/**
 * Creates the module of the project with the project root as its content root.
 * Saves the project settings first, so that the `.idea` directory exists.
 */
private fun TestFixture<GitSingleRepoContext>.projectModuleFixture(): TestFixture<Module> = testFixture {
  val context = init()
  val project = context.project
  saveSettings(project)
  val manager = ModuleManager.getInstance(project)
  val moduleFile = project.stateStore.projectBasePath.resolve("${project.name}.iml")
  val module = edtWriteAction {
    manager.newModule(moduleFile, EmptyModuleType.EMPTY_MODULE)
  }
  IndexingTestUtil.suspendUntilIndexesAreReady(project)
  edtWriteAction {
    module.addContentRoot(context.projectRoot)
  }
  initialized(module) {
    edtWriteAction {
      if (!module.isDisposed) {
        manager.disposeModule(module)
      }
    }
  }
}