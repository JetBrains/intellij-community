// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.ignore

import com.intellij.dvcs.ignore.IgnoredToExcludeNotificationProvider
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.VcsConfiguration
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.ui.EditorNotificationPanel
import com.intellij.util.io.createDirectories
import git4idea.repo.GitRepositoryFiles.GITIGNORE
import git4idea.test.GitSingleRepoContext
import git4idea.test.gitSingleRepoContextFixture
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.io.path.writeText
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

const val GEN = "gen"

private val TEST_TIMEOUT: Duration = 5.seconds

@TestApplication
@RegistryKey(key = "vcs.enable.add.ignored.directories.to.exclude", value = "true")
@RegistryKey(key = "vcs.propose.add.ignored.directories.to.exclude", value = "true")
internal class GitIgnoredToExcludedSynchronizerTest {
  private val fixture = gitSingleRepoContextFixture()
  private val context: GitSingleRepoContext get() = fixture.get()

  @TestDisposable
  lateinit var testDisposable: Disposable

  private lateinit var module: Module
  private lateinit var out: VirtualFile
  private lateinit var excluded: VirtualFile
  private lateinit var gen: VirtualFile

  @BeforeEach
  fun setUp(): Unit = with(context) {
    timeoutRunBlocking {
      val moduleDir = writeAction {
        module = ModuleManager.getInstance(project).newModule("$projectPath/main.iml", "EMPTY_MODULE")
        IndexingTestUtil.waitUntilIndexesAreReady(project)
        projectNioRoot.createDirectories()
        projectRoot
      }
      module.addContentRoot(moduleDir)
      writeAction {
        out = moduleDir.findOrCreateDir(OUT).apply { findOrCreateChildData(this, "a.class") }
        excluded = moduleDir.findOrCreateDir(EXCLUDED).apply { findOrCreateChildData(this, "b.class") }
        gen = moduleDir.findOrCreateDir(GEN).apply { findOrCreateChildData(this, "a.java") }
      }
      module.addSourceFolder(gen)
    }
  }

  @Test
  fun `test mark ignored directories as excluded notification`(): Unit = with(context) {
    timeoutRunBlocking(TEST_TIMEOUT) {
      assertThat(module.excludes()).isEmpty()

      createGitignoreAndWait("""
                            /$EXCLUDED/
                            /$OUT/
                           """.trimIndent())

      assertNotificationByContent(VcsBundle.message("ignore.to.exclude.notification.message"))
    }
  }

  @Test
  fun `test mark ignored directories as excluded`(): Unit = with(context) {
    timeoutRunBlocking(TEST_TIMEOUT) {
      VcsConfiguration.getInstance(project).MARK_IGNORED_AS_EXCLUDED = true

      assertThat(module.excludes()).isEmpty()

      createGitignoreAndWait("""
                            /$EXCLUDED/
                            /$OUT/
                           """.trimIndent())

      assertExcludedDirs(out, excluded)
    }
  }

  @Test
  fun `test do not mark ignored source root directory as excluded`(): Unit = with(context) {
    timeoutRunBlocking(TEST_TIMEOUT) {
      VcsConfiguration.getInstance(project).MARK_IGNORED_AS_EXCLUDED = true

      assertSourceDirs(gen)

      createGitignoreAndWait("""
                            /$EXCLUDED/
                            /$OUT/
                            /$GEN/
                           """.trimIndent())

      assertExcludedDirs(out, excluded)
      assertSourceDirs(gen)
    }
  }

  private suspend fun GitSingleRepoContext.createGitignoreAndWait(gitignoreContent: String) {
    val gitIgnorePath = repo.root.toNioPath().resolve(GITIGNORE)
    gitIgnorePath.writeText(gitignoreContent)
    checkNotNull(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(gitIgnorePath)) //trigger VFS create event explicitly

    repo.untrackedFilesHolder.awaitNotBusy()
  }

  private fun GitSingleRepoContext.assertNotificationByContent(notificationContent: String) {
    val gitIgnorePath = repo.root.toNioPath().resolve(GITIGNORE)
    val gitIgnoreVF = checkNotNull(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(gitIgnorePath))
    val editor = checkNotNull(
      invokeAndWaitIfNeeded { FileEditorManager.getInstance(project).openFile(gitIgnoreVF, false) }.firstOrNull()
    ) { "Editor for $gitIgnoreVF not found" }

    val notificationPanel =
      IgnoredToExcludeNotificationProvider().collectNotificationData(project, gitIgnoreVF)?.apply(editor) as? EditorNotificationPanel
    assertThat(notificationPanel?.text).describedAs("Notification %s not found", notificationContent).isEqualTo(notificationContent)
  }

  private fun assertExcludedDirs(vararg expectedExcludes: VirtualFile) {
    val excludes = module.excludes()
    assertThat(excludes).contains(*expectedExcludes)
  }

  private fun assertSourceDirs(vararg expectedSources: VirtualFile) {
    val sourceRoots = module.sourceRoots()
    assertThat(sourceRoots).contains(*expectedSources)
  }

  private fun Module.sourceRoots() = invokeAndWaitIfNeeded { ModuleRootManager.getInstance(this).sourceRoots.toList() }
  private fun Module.excludes() = invokeAndWaitIfNeeded { ModuleRootManager.getInstance(this).excludeRoots.toList() }
}