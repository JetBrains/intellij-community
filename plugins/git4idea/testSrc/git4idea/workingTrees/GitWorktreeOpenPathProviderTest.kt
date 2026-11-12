// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.workingTrees

import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.IoTestUtil
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.Path

@TestApplication
@Timeout(30)
internal class GitWorktreeOpenPathProviderTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val project: Project get() = projectFixture.get()

  @TestDisposable
  private lateinit var testRootDisposable: Disposable

  @Test
  fun `test reuses open project for alternate worktree entry point`(): Unit = timeoutRunBlocking {
    val worktreePath = Path(project.basePath!!)
    val entryPoint = Files.createTempFile(worktreePath, "worktree-", ".project-identity")
    try {
      ExtensionTestUtil.maskExtensions(
        GitWorktreeOpenPathProvider.EP_NAME,
        listOf(TestProvider { _, _ -> entryPoint }),
        testRootDisposable,
      )
      assertSame(project, ProjectUtil.findProject(worktreePath))

      assertSame(project, GitWorkingTreesService.getInstance(project).openProjectInNewWindow(worktreePath))
    }
    finally {
      Files.deleteIfExists(entryPoint)
    }
  }

  @Test
  fun `test does not reuse project with a different identity`(@TempDir otherProjectPath: Path): Unit = timeoutRunBlocking {
    val worktreePath = Path(project.basePath!!)
    val entryPoint = Files.createFile(otherProjectPath.resolve("worktree.project-identity"))
    ExtensionTestUtil.maskExtensions(
      GitWorktreeOpenPathProvider.EP_NAME,
      listOf(TestProvider { _, _ -> entryPoint }),
      testRootDisposable,
    )
    assertSame(project, ProjectUtil.findProject(worktreePath))

    assertNull(GitWorkingTreesService.getInstance(project).openProjectInNewWindow(worktreePath))
  }

  @Test
  fun `test reuses open project through a symbolic link`(): Unit = timeoutRunBlocking {
    IoTestUtil.assumeSymLinkCreationIsSupported()
    val worktreePath = Path(project.basePath!!)
    val entryPoint = Files.createTempFile(worktreePath, "worktree-", ".project-identity")
    val aliasPath = worktreePath.resolveSibling("${worktreePath.fileName}-alias")
    try {
      Files.createSymbolicLink(aliasPath, worktreePath)
      ExtensionTestUtil.maskExtensions(
        GitWorktreeOpenPathProvider.EP_NAME,
        listOf(TestProvider { _, _ -> aliasPath.resolve(entryPoint.fileName) }),
        testRootDisposable,
      )

      assertSame(project, GitWorkingTreesService.getInstance(project).openProjectInNewWindow(aliasPath))
    }
    finally {
      Files.deleteIfExists(aliasPath)
      Files.deleteIfExists(entryPoint)
    }
  }

  @Test
  fun `test provider can override worktree root`(): Unit = timeoutRunBlocking {
    val worktreePath = Path("/tmp/worktree")
    val bazelPath = worktreePath.resolve("MODULE.bazel")

    ExtensionTestUtil.maskExtensions(
      GitWorktreeOpenPathProvider.EP_NAME,
      listOf(TestProvider { _, _ -> bazelPath }),
      testRootDisposable,
    )

    assertEquals(bazelPath, resolveWorktreeOpenPath(project, worktreePath))
  }

  @Test
  fun `test falls back to worktree root when no provider handles it`(): Unit = timeoutRunBlocking {
    val worktreePath = Path("/tmp/worktree")

    ExtensionTestUtil.maskExtensions(
      GitWorktreeOpenPathProvider.EP_NAME,
      listOf(TestProvider { _, _ -> null }),
      testRootDisposable,
    )

    assertEquals(worktreePath, resolveWorktreeOpenPath(project, worktreePath))
  }

  @Test
  fun `test falls back to worktree root when provider hits dumb mode`(): Unit = timeoutRunBlocking {
    val worktreePath = Path("/tmp/worktree")

    ExtensionTestUtil.maskExtensions(
      GitWorktreeOpenPathProvider.EP_NAME,
      listOf(TestProvider { _, _ -> throw IndexNotReadyException.create() }),
      testRootDisposable,
    )

    assertEquals(worktreePath, resolveWorktreeOpenPath(project, worktreePath))
  }

  private class TestProvider(
    private val resolver: suspend (Project, Path) -> Path?,
  ) : GitWorktreeOpenPathProvider {
    override suspend fun getPathToOpen(project: Project, worktreePath: Path): Path? = resolver(project, worktreePath)
  }
}
