// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.workingTrees

import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.platform.testFramework.junit5.eel.params.api.DockerTest
import com.intellij.platform.testFramework.junit5.eel.params.api.EelHolder
import com.intellij.platform.testFramework.junit5.eel.params.api.TestApplicationWithEel
import git4idea.test.GitSingleRepoContext
import git4idea.test.gitSingleRepoContextFixture
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.OS
import org.junit.jupiter.params.ParameterizedClass
import java.nio.file.Files

/**
 * Runs [GitWorktreeProjectConfigService.copyAndCleanUpWorktreeIncludeFiles] with the source repository and the target
 * worktree directory backed by an Eel filesystem, not the JVM default one. [GitWorktreeProjectConfigService.copyConfigFile]
 * needs no change for this: a plain `java.nio.file.Path`/`Files.*` call is already Eel-routed. These tests
 * exist to check that claim, and to check that [GitWorktreeProjectConfigService.remapAbsolutePaths] compares
 * [java.nio.file.Path]s on the root's own file system rather than the default one.
 *
 * A docker leg only runs when a docker daemon is available; it is never required.
 */
@TestApplicationWithEel(osesMayNotHaveRemoteEels = [OS.WINDOWS, OS.LINUX, OS.MAC])
@ParameterizedClass
@DockerTest(image = "alpine/git", mandatory = false)
internal class GitWorktreeProjectConfigServiceEelTest(@Suppress("unused") val eelHolder: EelHolder) {
  private val contextFixture = gitSingleRepoContextFixture()
  private val context: GitSingleRepoContext get() = contextFixture.get()

  @Test
  fun `test remaps a literal absolute source root path when both roots are eel-resolved`(): Unit = with(context) {
    touch(".worktreeinclude", ".idea/*.xml\n")
    touch(
      ".idea/workspace.xml",
      """
        <?xml version="1.0" encoding="UTF-8"?>
        <project version="4">
          <component name="RunManager" selected="Application.Foo">
            <configuration name="Foo" type="Application">
              <option name="WORKING_DIRECTORY" value="${repo.root.path}" />
            </configuration>
          </component>
        </project>
      """.trimIndent(),
    )

    val targetDir = testNioRoot.resolve("target")
    Files.createDirectories(targetDir)
    // The opened project's own root is allowed by the test framework; this sibling directory, the target of
    // the new worktree, is not, until refreshCopiedFiles's VFS refresh needs it allowed too.
    VfsRootAccess.allowRootAccess(project, targetDir.toString())

    runBlocking {
      GitWorktreeProjectConfigService.getInstance(project).copyAndCleanUpWorktreeIncludeFiles(repo.root, targetDir)
    }

    val targetWorkspace = JDOMUtil.load(targetDir.resolve(".idea/workspace.xml"))
    val runManager = targetWorkspace.getChildren("component").first { it.getAttributeValue("name") == "RunManager" }
    val workingDirectory = runManager.getChild("configuration")!!.getChildren("option")
      .first { it.getAttributeValue("name") == "WORKING_DIRECTORY" }.getAttributeValue("value")
    assertThat(workingDirectory).isEqualTo(targetDir.toString())
  }

  @Test
  fun `test bulk-copies worktreeinclude matches without failure when both roots are eel-resolved`(): Unit = with(context) {
    touch(
      ".idea/vcs.xml",
      $$"""
        <?xml version="1.0" encoding="UTF-8"?>
        <project version="4">
          <component name="VcsDirectoryMappings">
            <mapping directory="$PROJECT_DIR$" vcs="Git" />
          </component>
        </project>
        """.trimIndent(),
    )
    touch(".worktreeinclude", ".idea/*.xml\nnotes/*.md\n")
    touch("notes/todo.md", "buy milk")

    val targetDir = testNioRoot.resolve("target")
    Files.createDirectories(targetDir)
    VfsRootAccess.allowRootAccess(project, targetDir.toString())

    val failedFiles = runBlocking {
      GitWorktreeProjectConfigService.getInstance(project).copyAndCleanUpWorktreeIncludeFiles(repo.root, targetDir)
    }

    assertThat(failedFiles).isEmpty()
    assertThat(targetDir.resolve(".idea/vcs.xml")).exists()
    assertThat(Files.readString(targetDir.resolve("notes/todo.md"))).isEqualTo("buy milk")
  }
}
