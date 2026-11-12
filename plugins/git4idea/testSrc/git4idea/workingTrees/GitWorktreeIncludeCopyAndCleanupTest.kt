// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.workingTrees

import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.io.IoTestUtil
import com.intellij.testFramework.junit5.TestApplication
import git4idea.test.GitSingleRepoContext
import git4idea.test.file
import git4idea.test.gitSingleRepoContextFixture
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText

/**
 * Tests [GitWorktreeProjectConfigService.copyAndCleanUpWorktreeIncludeFiles], the single copy mechanism for both idea
 * settings and any other .worktreeinclude match, and its post-copy cleanup pass.
 */
@TestApplication
internal class GitWorktreeIncludeCopyAndCleanupTest {
  private val contextFixture = gitSingleRepoContextFixture()
  private val context: GitSingleRepoContext get() = contextFixture.get()

  @Test
  fun `test copies an untracked file that matches a worktreeinclude pattern`(): Unit = with(context) {
    touch(".worktreeinclude", "notes/*.md\n")
    touch("notes/todo.md", "buy milk")

    val targetDir = testNioRoot.resolve("target")
    Files.createDirectories(targetDir)

    runBlocking {
      GitWorktreeProjectConfigService.getInstance(project).copyAndCleanUpWorktreeIncludeFiles(repo.root, targetDir)
    }

    assertThat(Files.readString(targetDir.resolve("notes/todo.md"))).isEqualTo("buy milk")
  }

  @Test
  fun `test does not overwrite a file already present in the target`(): Unit = with(context) {
    touch(".worktreeinclude", "notes/*.md\n")
    touch("notes/todo.md", "buy milk")

    val targetDir = testNioRoot.resolve("target")
    val targetFile = targetDir.resolve("notes/todo.md")
    Files.createDirectories(targetFile.parent)
    Files.writeString(targetFile, "existing target content")

    runBlocking {
      GitWorktreeProjectConfigService.getInstance(project).copyAndCleanUpWorktreeIncludeFiles(repo.root, targetDir)
    }

    assertThat(Files.readString(targetFile)).isEqualTo("existing target content")
  }

  @Test
  fun `test does not copy a worktreeinclude match that is tracked in git`(): Unit = with(context) {
    touch(".worktreeinclude", "config/local.txt\n")
    repo.file("config/local.txt").write("committed").addCommit("Track local.txt")
    repo.file("config/local.txt").write("modified")

    val targetDir = testNioRoot.resolve("target")
    Files.createDirectories(targetDir)

    runBlocking {
      GitWorktreeProjectConfigService.getInstance(project).copyAndCleanUpWorktreeIncludeFiles(repo.root, targetDir)
    }

    assertThat(targetDir.resolve("config/local.txt")).doesNotExist()
  }

  @Test
  fun `test copies a worktreeinclude match that is staged but not committed`(): Unit = with(context) {
    touch(".worktreeinclude", "notes/*.md\n")
    repo.file("notes/todo.md").create("buy milk").add()

    val targetDir = testNioRoot.resolve("target")
    Files.createDirectories(targetDir)

    runBlocking {
      GitWorktreeProjectConfigService.getInstance(project).copyAndCleanUpWorktreeIncludeFiles(repo.root, targetDir)
    }

    assertThat(Files.readString(targetDir.resolve("notes/todo.md"))).isEqualTo("buy milk")
  }

  @Test
  fun `test copies the worktreeinclude file itself when it is staged but not committed`(): Unit = with(context) {
    repo.file(WORKTREE_INCLUDE_FILE_NAME).create("$WORKTREE_INCLUDE_FILE_NAME\nnotes/*.md\n").add()
    touch("notes/todo.md", "buy milk")

    val targetDir = testNioRoot.resolve("target")
    Files.createDirectories(targetDir)

    runBlocking {
      GitWorktreeProjectConfigService.getInstance(project).copyAndCleanUpWorktreeIncludeFiles(repo.root, targetDir)
    }

    assertThat(Files.readString(targetDir.resolve(WORKTREE_INCLUDE_FILE_NAME))).isEqualTo("$WORKTREE_INCLUDE_FILE_NAME\nnotes/*.md\n")
    assertThat(Files.readString(targetDir.resolve("notes/todo.md"))).isEqualTo("buy milk")
  }

  @Test
  fun `test worktreeinclude pattern matching nothing is a no-op`(): Unit = with(context) {
    touch(".worktreeinclude", "does-not-exist/*.txt\n")

    val targetDir = testNioRoot.resolve("target")
    Files.createDirectories(targetDir)

    runBlocking {
      GitWorktreeProjectConfigService.getInstance(project).copyAndCleanUpWorktreeIncludeFiles(repo.root, targetDir)
    }

    Files.list(targetDir).use { assertThat(it.count()).isZero() }
  }

  @Test
  fun `test honors a negated worktreeinclude pattern`(): Unit = with(context) {
    touch(".worktreeinclude", "notes/*\n!notes/skip.md\n")
    touch("notes/todo.md", "buy milk")
    touch("notes/skip.md", "do not copy")

    val targetDir = testNioRoot.resolve("target")
    Files.createDirectories(targetDir)

    runBlocking {
      GitWorktreeProjectConfigService.getInstance(project).copyAndCleanUpWorktreeIncludeFiles(repo.root, targetDir)
    }

    assertThat(targetDir.resolve("notes/todo.md")).exists()
    assertThat(targetDir.resolve("notes/skip.md")).doesNotExist()
  }

  @Test
  fun `test does nothing when no worktreeinclude file is present`(): Unit = with(context) {
    touch("notes/todo.md", "buy milk")

    val targetDir = testNioRoot.resolve("target")
    Files.createDirectories(targetDir)

    runBlocking {
      GitWorktreeProjectConfigService.getInstance(project).copyAndCleanUpWorktreeIncludeFiles(repo.root, targetDir)
    }

    assertThat(targetDir.resolve("notes/todo.md")).doesNotExist()
  }

  @Test
  fun `test a dangling symlink matching a worktreeinclude pattern is treated as absent without stopping other files from copying`(): Unit = with(context) {
    IoTestUtil.assumeSymLinkCreationIsSupported()
    touch(".worktreeinclude", "notes/*\n")
    touch("notes/todo.md", "buy milk")
    val notesDir = repo.root.toNioPath().resolve("notes")
    val danglingLink = notesDir.resolve("dangling.md")
    IoTestUtil.createSymLink(notesDir.resolve("does-not-exist.md").toString(), danglingLink.toString(), false)

    val targetDir = testNioRoot.resolve("target")
    Files.createDirectories(targetDir)

    val failedFiles = runBlocking {
      GitWorktreeProjectConfigService.getInstance(project).copyAndCleanUpWorktreeIncludeFiles(repo.root, targetDir)
    }

    assertThat(Files.readString(targetDir.resolve("notes/todo.md"))).isEqualTo("buy milk")
    assertThat(targetDir.resolve("notes/dangling.md")).doesNotExist()
    assertThat(failedFiles).isEmpty()
  }

  @Test
  fun `test copyConfigFile follows a valid symlink and copies the resolved content`(@TempDir tempDir: Path) {
    IoTestUtil.assumeSymLinkCreationIsSupported()

    val target = tempDir.resolve("target.txt").apply { writeText("resolved content") }
    val link = tempDir.resolve("link.txt")
    IoTestUtil.createSymLink(target.toString(), link.toString())

    val destination = tempDir.resolve("copy.txt")
    val succeeded = GitWorktreeProjectConfigService.copyConfigFile(link, destination)

    assertThat(succeeded).isTrue()
    assertThat(Files.readString(destination)).isEqualTo("resolved content")
  }

  @Test
  fun `test copyConfigFile reports a failure instead of throwing for a dangling symlink`(@TempDir tempDir: Path) {
    IoTestUtil.assumeSymLinkCreationIsSupported()

    val danglingTarget = tempDir.resolve("does-not-exist.txt")
    val link = tempDir.resolve("dangling-link.txt")
    IoTestUtil.createSymLink(danglingTarget.toString(), link.toString(), false)

    val destination = tempDir.resolve("copy.txt")
    val succeeded = GitWorktreeProjectConfigService.copyConfigFile(link, destination)

    assertThat(succeeded).isFalse()
    assertThat(destination).doesNotExist()
  }

  @Test
  fun `test strips ProjectId and remaps an absolute path from a copied workspace xml`(): Unit = with(context) {
    touch(".worktreeinclude", ".idea/*.xml\n")
    touch(
      ".idea/workspace.xml",
      """
        <?xml version="1.0" encoding="UTF-8"?>
        <project version="4">
          <component name="ProjectId" id="someProjectId" />
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

    runBlocking {
      GitWorktreeProjectConfigService.getInstance(project).copyAndCleanUpWorktreeIncludeFiles(repo.root, targetDir)
    }

    val targetWorkspace = JDOMUtil.load(targetDir.resolve(".idea/workspace.xml"))
    val componentNames = targetWorkspace.getChildren("component").map { it.getAttributeValue("name") }
    assertThat(componentNames).containsExactly("RunManager")

    val runManager = targetWorkspace.getChildren("component").first { it.getAttributeValue("name") == "RunManager" }
    val workingDirectory = runManager.getChild("configuration")!!.getChildren("option")
      .first { it.getAttributeValue("name") == "WORKING_DIRECTORY" }.getAttributeValue("value")
    assertThat(workingDirectory).isEqualTo(targetDir.toString())
  }

  @Test
  fun `test remaps an absolute path embedded inside a JSON attribute value`(): Unit = with(context) {
    touch(".worktreeinclude", ".idea/*.xml\n")
    touch(
      ".idea/workspace.xml",
      """
        <?xml version="1.0" encoding="UTF-8"?>
        <project version="4">
          <component name="RunManager" state="{&quot;last_opened_file_path&quot;:&quot;${repo.root.path}&quot;,&quot;other&quot;:1}" />
        </project>
      """.trimIndent(),
    )

    val targetDir = testNioRoot.resolve("target")
    Files.createDirectories(targetDir)

    runBlocking {
      GitWorktreeProjectConfigService.getInstance(project).copyAndCleanUpWorktreeIncludeFiles(repo.root, targetDir)
    }

    val targetWorkspace = JDOMUtil.load(targetDir.resolve(".idea/workspace.xml"))
    val runManager = targetWorkspace.getChildren("component").first { it.getAttributeValue("name") == "RunManager" }
    assertThat(runManager.getAttributeValue("state"))
      .isEqualTo("""{"last_opened_file_path":"$targetDir","other":1}""")
  }

  @Test
  fun `test does not touch a copied file outside idea and run directories`(): Unit = with(context) {
    touch(".worktreeinclude", "config/*.xml\n")
    touch(
      "config/local.xml",
      """<?xml version="1.0" encoding="UTF-8"?><settings path="${repo.root.path}" />""",
    )

    val targetDir = testNioRoot.resolve("target")
    Files.createDirectories(targetDir)

    runBlocking {
      GitWorktreeProjectConfigService.getInstance(project).copyAndCleanUpWorktreeIncludeFiles(repo.root, targetDir)
    }

    val targetXml = JDOMUtil.load(targetDir.resolve("config/local.xml"))
    assertThat(targetXml.getAttributeValue("path")).isEqualTo(repo.root.path)
  }

  @Test
  fun `test removeComponent removes an existing component`(): Unit = with(context) {
    touch(
      "workspace.xml",
      """
        <?xml version="1.0" encoding="UTF-8"?>
        <project version="4">
          <component name="ProjectId" id="someProjectId" />
          <component name="FormatOnSaveOptions" />
        </project>
      """.trimIndent(),
    )
    val root = JDOMUtil.load(repo.root.toNioPath().resolve("workspace.xml"))

    GitWorktreeProjectConfigService.removeComponent(root, "ProjectId")

    val componentNames = root.getChildren("component").map { it.getAttributeValue("name") }
    assertThat(componentNames).containsExactly("FormatOnSaveOptions")
  }

  @Test
  fun `test removeComponent is a no-op when the component is absent`(): Unit = with(context) {
    touch(
      "workspace.xml",
      """
        <?xml version="1.0" encoding="UTF-8"?>
        <project version="4">
          <component name="FormatOnSaveOptions" />
        </project>
      """.trimIndent(),
    )
    val root = JDOMUtil.load(repo.root.toNioPath().resolve("workspace.xml"))

    GitWorktreeProjectConfigService.removeComponent(root, "ProjectId")

    val componentNames = root.getChildren("component").map { it.getAttributeValue("name") }
    assertThat(componentNames).containsExactly("FormatOnSaveOptions")
  }
}
