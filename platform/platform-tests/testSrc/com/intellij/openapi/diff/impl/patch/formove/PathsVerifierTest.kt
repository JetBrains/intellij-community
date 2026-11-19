// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.diff.impl.patch.formove

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.diff.impl.patch.BinaryFilePatch
import com.intellij.openapi.diff.impl.patch.FilePatch
import com.intellij.openapi.diff.impl.patch.PatchReader
import com.intellij.openapi.diff.impl.patch.TextFilePatch
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.vcs.AbstractVcsHelper
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsDirectoryMapping
import com.intellij.openapi.vcs.actions.VcsContextFactory
import com.intellij.openapi.vcs.changes.committed.MockAbstractVcs
import com.intellij.openapi.vcs.impl.ProjectLevelVcsManagerImpl
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.peer.impl.VcsContextFactoryImpl
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import com.intellij.testFramework.registerOrReplaceServiceInstance
import io.mockk.Called
import io.mockk.MockKMatcherScope
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * Tests for [PathsVerifier].
 *
 * Each test calls the verifier in the order that [PatchApplier] uses.
 * First [PathsVerifier.nonWriteActionPreCheck], then [PathsVerifier.execute] in a write action, then [PathsVerifier.doMoveIfNeeded].
 *
 * The test tree has four directories in one temporary root.
 * `project` is the project base dir and a content root.
 * `lib` is a second content root next to the project.
 * `vcs` is a VCS root in the [UnderVcsRoot] tests only.
 * `outside` is not in the project content and has no VCS root.
 *
 * A patch can use `..` to get to a content root or a VCS root.
 * A patch must not create, change or move a file outside them.
 */
@TestApplication
internal class PathsVerifierTest {
  private val rootFixture = tempPathFixture()
  private val projectDirFixture = rootFixture.childDirFixture("project")
  private val libDirFixture = rootFixture.childDirFixture("lib")
  private val vcsDirFixture = rootFixture.childDirFixture("vcs")
  private val outsideDirFixture = rootFixture.childDirFixture("outside")
  private val projectFixture = projectFixture(pathFixture = projectDirFixture)
  private val moduleFixture = projectFixture.moduleFixture("main")

  private val project: Project get() = projectFixture.get()
  private val projectDir: Path get() = projectDirFixture.get()
  private val libDir: Path get() = libDirFixture.get()
  private val vcsDir: Path get() = vcsDirFixture.get()
  private val outsideDir: Path get() = outsideDirFixture.get()

  /**
   * Gives the answer to the overwrite prompt of [PathsVerifier].
   * The real helper shows a dialog, and a dialog fails in unit test mode.
   * The default answer confirms all files.
   */
  private val vcsHelper: AbstractVcsHelper = mockk()

  private lateinit var baseDir: VirtualFile

  @BeforeEach
  fun setUp(@TestDisposable disposable: Disposable) {
    ApplicationManager.getApplication()
      .registerOrReplaceServiceInstance(VcsContextFactory::class.java, VcsContextFactoryImpl(), disposable)
    every { overwritePrompt(vcsHelper) } answers { firstArg() }
    project.registerOrReplaceServiceInstance(AbstractVcsHelper::class.java, vcsHelper, disposable)

    assertTrue(FileTypeManager.getInstance().isFileIgnored(IGNORED_DIR_NAME))
    val root = findFile(rootFixture.get())
    VfsUtil.markDirtyAndRefresh(false, true, true, root)
    baseDir = findFile(projectDir)
    val libRoot = findFile(libDir)
    findFile(vcsDir)
    findFile(outsideDir)
    ModuleRootModificationUtil.updateModel(moduleFixture.get()) { model ->
      model.addContentEntry(baseDir)
      model.addContentEntry(libRoot)
    }
  }

  @AfterEach
  fun tearDown() {
    unmockkAll()
  }

  @Nested
  inner class Modified {
    @Test
    fun `a modified file in the project is accepted`(): Unit = timeoutRunBlocking {
      val file = writeFile(projectDir.resolve("src/A.txt"))
      val patch = modified("src/A.txt")

      val outcome = runVerifier(patch)

      assertTrue(outcome.isAccepted(patch))
      assertEquals(listOf(file), outcome.verifier.textPatches.map { it.file })
      assertEquals(listOf(file), outcome.verifier.writableFiles)
      assertTrue(file.path in outcome.verifier.directlyAffected.map { it.path })
    }

    @Test
    fun `a modified file that does not exist fails the precheck`(): Unit = timeoutRunBlocking {
      val patch = modified("src/A.txt")

      val outcome = runVerifier(patch)

      assertEquals(listOf(patch), outcome.precheckFailed)
      assertTrue(outcome.verifier.textPatches.isEmpty())
    }

    @Test
    fun `a binary patch goes to the binary patches`(): Unit = timeoutRunBlocking {
      val file = writeFile(projectDir.resolve("src/A.txt"))
      val patch = BinaryFilePatch(byteArrayOf(1), byteArrayOf(2)).apply {
        beforeName = "src/A.txt"
        afterName = "src/A.txt"
      }

      val outcome = runVerifier(patch)

      assertTrue(outcome.isAccepted(patch))
      assertEquals(listOf(file), outcome.verifier.binaryPatches.map { it.file })
      assertTrue(outcome.verifier.textPatches.isEmpty())
    }

    @Test
    fun `a text patch for a directory is filtered out`(): Unit = timeoutRunBlocking {
      projectDir.resolve("src/dir").createDirectories()
      val patch = modified("src/dir")

      val outcome = runVerifier(patch)

      assertEquals(listOf<FilePatch>(patch), outcome.verifier.filterBadFileTypePatches().toList())
      assertTrue(outcome.verifier.textPatches.isEmpty())
    }

    @Test
    fun `a modified file outside the project is rejected`(): Unit = timeoutRunBlocking {
      writeFile(outsideDir.resolve("A.txt"))
      val patch = modified("../outside/A.txt")

      val outcome = runVerifier(patch)

      assertTrue(outcome.isRejected(patch))
      assertTrue(outcome.verifier.writableFiles.isEmpty())
    }

    @Test
    fun `a modified file outside the project is accepted when the content roots check is off`(): Unit = timeoutRunBlocking {
      val file = writeFile(outsideDir.resolve("A.txt"))
      val patch = modified("../outside/A.txt")

      val outcome = runVerifier(patch) { it.ignoreContentRootsCheck = true }

      assertTrue(outcome.isAccepted(patch))
      assertEquals(listOf(file), outcome.verifier.writableFiles)
    }
  }

  @Nested
  inner class Added {
    @Test
    fun `an added file in a new directory creates the directory and the file`(): Unit = timeoutRunBlocking {
      val patch = added("src/new/New.txt")

      val outcome = runVerifier(patch)

      assertTrue(outcome.isAccepted(patch))
      val newFile = projectDir.resolve("src/new/New.txt")
      assertTrue(newFile.isRegularFile())
      assertEquals(listOf(systemIndependent(newFile)), outcome.verifier.toBeAdded.map { it.path })
      assertTrue(systemIndependent(projectDir.resolve("src/new")) in outcome.verifier.directlyAffected.map { it.path })
      verify { vcsHelper wasNot Called }
    }

    @Test
    fun `an added file that exists is reused when the user confirms the overwrite`(): Unit = timeoutRunBlocking {
      val existing = writeFile(projectDir.resolve("src/Existing.txt"), "old")
      val patch = added("src/Existing.txt")

      val outcome = runVerifier(patch)

      assertTrue(outcome.isAccepted(patch))
      val files = slot<List<FilePath>>()
      verify(exactly = 1) { overwritePrompt(vcsHelper, capture(files)) }
      assertEquals(listOf(existing.path), files.captured.map { it.path })
      assertEquals(listOf(existing), outcome.verifier.textPatches.map { it.file })
      assertEquals("old", projectDir.resolve("src/Existing.txt").readText())
    }

    @Test
    fun `an added file that exists is skipped when the user declines the overwrite`(): Unit = timeoutRunBlocking {
      writeFile(projectDir.resolve("src/Existing.txt"), "old")
      every { overwritePrompt(vcsHelper) } returns null
      val patch = added("src/Existing.txt")

      val outcome = runVerifier(patch)

      assertEquals(listOf<FilePatch>(patch), outcome.skipped)
      assertFalse(outcome.isRejected(patch))
      assertTrue(outcome.verifier.textPatches.isEmpty())
      assertEquals("old", projectDir.resolve("src/Existing.txt").readText())
    }

    @Test
    fun `an added file with a dot-dot path that stays in the project is accepted`(): Unit = timeoutRunBlocking {
      projectDir.resolve("src").createDirectories()
      val patch = added("src/../other/New.txt")

      val outcome = runVerifier(patch)

      assertTrue(outcome.isAccepted(patch))
      assertTrue(projectDir.resolve("other/New.txt").isRegularFile())
    }

    @Test
    fun `an added file with a path that goes through outside the project creates nothing outside`(): Unit = timeoutRunBlocking {
      projectDir.resolve("src").createDirectories()
      val patch = added("../outside/new/../../${projectDir.fileName}/src/New.txt")

      val outcome = runVerifier(patch)

      assertTrue(outcome.isAccepted(patch))
      assertTrue(projectDir.resolve("src/New.txt").isRegularFile())
      assertFalse(outsideDir.resolve("new").exists(), "The verifier must not create a directory outside the project")
    }

    @Test
    fun `an added file outside the project is rejected and creates nothing outside`(): Unit = timeoutRunBlocking {
      val patch = added("../outside/new/New.txt")

      val outcome = runVerifier(patch)

      assertTrue(outcome.isRejected(patch))
      assertFalse(outsideDir.resolve("new").exists(), "The verifier must not create a directory outside the project")
      assertTrue(outcome.verifier.toBeAdded.isEmpty())
      assertTrue(outcome.verifier.textPatches.isEmpty())
    }

    @Test
    fun `an added file that exists outside the project is rejected without an overwrite prompt`(): Unit = timeoutRunBlocking {
      writeFile(outsideDir.resolve("Existing.txt"), "old")
      val patch = added("../outside/Existing.txt")

      val outcome = runVerifier(patch)

      assertTrue(outcome.isRejected(patch))
      verify { vcsHelper wasNot Called }
      assertTrue(outcome.verifier.textPatches.isEmpty())
      assertEquals("old", outsideDir.resolve("Existing.txt").readText())
    }

    @Test
    fun `an added file in a new directory with an ignored name is rejected`(): Unit = timeoutRunBlocking {
      projectDir.resolve("src").createDirectories()
      val patch = added("src/$IGNORED_DIR_NAME/config")

      val outcome = runVerifier(patch)

      assertTrue(outcome.isRejected(patch))
      assertFalse(projectDir.resolve("src/$IGNORED_DIR_NAME").exists())
      assertTrue(outcome.verifier.toBeAdded.isEmpty())
    }

    @Test
    fun `an added file with more dot-dot segments than the path depth is rejected`(): Unit = timeoutRunBlocking {
      val patch = added(climbAboveFileSystemRoot() + "$DEEP_DIR_NAME/New.txt")

      val outcome = runVerifier(patch)

      assertTrue(outcome.isRejected(patch))
      assertFalse(projectDir.root.resolve(DEEP_DIR_NAME).exists())
      assertTrue(outcome.verifier.toBeAdded.isEmpty())
    }
  }

  @Nested
  inner class Deleted {
    @Test
    fun `a deleted file in the project goes to the paths to delete`(): Unit = timeoutRunBlocking {
      val file = writeFile(projectDir.resolve("src/A.txt"))
      val patch = deleted("src/A.txt")

      val outcome = runVerifier(patch)

      assertTrue(outcome.isAccepted(patch))
      assertEquals(listOf(file.path), outcome.verifier.toBeDeleted.map { it.path })
      assertEquals(listOf(file), outcome.verifier.textPatches.map { it.file })
    }

    @Test
    fun `a deleted file that is already gone is skipped`(): Unit = timeoutRunBlocking {
      val patch = deleted("src/A.txt")

      val outcome = runVerifier(patch)

      assertEquals(listOf<FilePatch>(patch), outcome.skipped)
      assertFalse(outcome.isRejected(patch))
      assertTrue(outcome.verifier.toBeDeleted.isEmpty())
    }

    @Test
    fun `a deleted file outside the project is rejected`(): Unit = timeoutRunBlocking {
      writeFile(outsideDir.resolve("A.txt"))
      val patch = deleted("../outside/A.txt")

      val outcome = runVerifier(patch)

      assertTrue(outcome.isRejected(patch))
      assertTrue(outcome.verifier.toBeDeleted.isEmpty())
    }
  }

  @Nested
  inner class Moved {
    @Test
    fun `a moved file in the project goes to the new path`(): Unit = timeoutRunBlocking {
      writeFile(projectDir.resolve("src/A.txt"), "old")
      val patch = moved("src/A.txt", "dst/B.txt")

      val outcome = runVerifier(patch)
      applyMoves(outcome)

      assertTrue(outcome.isAccepted(patch))
      assertEquals("old", projectDir.resolve("dst/B.txt").readText())
      assertFalse(projectDir.resolve("src/A.txt").exists())
    }

    @Test
    fun `a move keeps a file with the new name in the old directory`(): Unit = timeoutRunBlocking {
      writeFile(projectDir.resolve("src/A.txt"), "moved")
      writeFile(projectDir.resolve("src/B.txt"), "stays")
      val patch = moved("src/A.txt", "dst/B.txt")

      val outcome = runVerifier(patch)
      applyMoves(outcome)

      assertTrue(outcome.isAccepted(patch))
      assertEquals("moved", projectDir.resolve("dst/B.txt").readText())
      assertEquals("stays", projectDir.resolve("src/B.txt").readText())
      assertEquals(listOf("B.txt"), fileNames(projectDir.resolve("src")), "The move must leave no temporary file")
      assertEquals(listOf("B.txt"), fileNames(projectDir.resolve("dst")), "The move must leave no temporary file")
    }

    @Test
    fun `a move fails the precheck when the target exists`(): Unit = timeoutRunBlocking {
      writeFile(projectDir.resolve("src/A.txt"))
      writeFile(projectDir.resolve("dst/B.txt"))
      val patch = moved("src/A.txt", "dst/B.txt")

      val outcome = runVerifier(patch)

      assertEquals(listOf(patch), outcome.precheckFailed)
    }

    @Test
    fun `a move fails the precheck when the source does not exist`(): Unit = timeoutRunBlocking {
      val patch = moved("src/A.txt", "dst/B.txt")

      val outcome = runVerifier(patch)

      assertEquals(listOf(patch), outcome.precheckFailed)
    }

    @Test
    fun `a move from outside the project is rejected`(): Unit = timeoutRunBlocking {
      writeFile(outsideDir.resolve("A.txt"))
      val patch = moved("../outside/A.txt", "src/A.txt")

      val outcome = runVerifier(patch)
      applyMoves(outcome)

      assertTrue(outcome.isRejected(patch))
      assertTrue(outsideDir.resolve("A.txt").isRegularFile())
      assertFalse(projectDir.resolve("src/A.txt").exists())
    }

    @Test
    fun `a move to outside the project is rejected and the file stays`(): Unit = timeoutRunBlocking {
      writeFile(projectDir.resolve("src/A.txt"))
      val patch = moved("src/A.txt", "../outside/new/A.txt")

      val outcome = runVerifier(patch)
      applyMoves(outcome)

      assertTrue(outcome.isRejected(patch))
      assertTrue(projectDir.resolve("src/A.txt").isRegularFile())
      assertFalse(outsideDir.resolve("new").exists(), "The verifier must not create a directory outside the project")
    }

    @Test
    fun `a git rename to outside the project is rejected and the file stays`(): Unit = timeoutRunBlocking {
      writeFile(projectDir.resolve("src/App.txt"), "x\n")
      val patch = PatchReader(
        """
        diff --git a/src/App.txt b/src/App.txt
        similarity index 50%
        rename from src/App.txt
        rename to ../outside/new/App.plist
        --- a/src/App.txt
        +++ b/src/App.txt
        @@ -1 +1 @@
        -x
        +y
        """.trimIndent() + "\n"
      ).readTextPatches().single()
      assertEquals("../outside/new/App.plist", patch.afterName)

      val outcome = runVerifier(patch)
      applyMoves(outcome)

      assertTrue(outcome.isRejected(patch))
      assertTrue(projectDir.resolve("src/App.txt").isRegularFile())
      assertFalse(outsideDir.resolve("new").exists(), "The verifier must not create a directory outside the project")
    }

    @Test
    fun `a move to a new directory with an ignored name is rejected and the file stays`(): Unit = timeoutRunBlocking {
      writeFile(projectDir.resolve("src/A.txt"))
      val patch = moved("src/A.txt", "src/$IGNORED_DIR_NAME/A.txt")

      val outcome = runVerifier(patch)
      applyMoves(outcome)

      assertTrue(outcome.isRejected(patch))
      assertTrue(projectDir.resolve("src/A.txt").isRegularFile())
      assertFalse(projectDir.resolve("src/$IGNORED_DIR_NAME").exists())
    }

    @Test
    fun `a move with more dot-dot segments than the path depth is rejected and the file stays`(): Unit = timeoutRunBlocking {
      writeFile(projectDir.resolve("src/A.txt"))
      val patch = moved("src/A.txt", climbAboveFileSystemRoot() + "$DEEP_DIR_NAME/A.txt")

      val outcome = runVerifier(patch)
      applyMoves(outcome)

      assertTrue(outcome.isRejected(patch))
      assertTrue(projectDir.resolve("src/A.txt").isRegularFile())
      assertFalse(projectDir.root.resolve(DEEP_DIR_NAME).exists())
    }
  }

  @Nested
  inner class InContentRootOutsideProjectDir {
    @Test
    fun `an added file in a content root outside the project dir is accepted`(): Unit = timeoutRunBlocking {
      val patch = added("../lib/New.txt")

      val outcome = runVerifier(patch)

      assertTrue(outcome.isAccepted(patch))
      assertTrue(libDir.resolve("New.txt").isRegularFile())
    }

    @Test
    fun `a move to a content root outside the project dir is accepted`(): Unit = timeoutRunBlocking {
      writeFile(projectDir.resolve("src/A.txt"), "old")
      val patch = moved("src/A.txt", "../lib/A.txt")

      val outcome = runVerifier(patch)
      applyMoves(outcome)

      assertTrue(outcome.isAccepted(patch))
      assertEquals("old", libDir.resolve("A.txt").readText())
      assertFalse(projectDir.resolve("src/A.txt").exists())
    }
  }

  @Nested
  inner class UnderVcsRoot {
    @BeforeEach
    fun setUpVcsRoot(): Unit = timeoutRunBlocking {
      val vcs = MockAbstractVcs(project)
      val vcsManager = ProjectLevelVcsManager.getInstance(project) as ProjectLevelVcsManagerImpl
      vcsManager.registerVcs(vcs)
      vcsManager.setDirectoryMappings(listOf(VcsDirectoryMapping(systemIndependent(vcsDir), vcs.name)))
      vcsManager.awaitInitialization()
      assertEquals(findFile(vcsDir), vcsManager.getVcsRootFor(findFile(vcsDir)))
    }

    @Test
    fun `a modified file under a VCS root outside the project dir is accepted`(): Unit = timeoutRunBlocking {
      val file = writeFile(vcsDir.resolve("A.txt"))
      val patch = modified("../vcs/A.txt")

      val outcome = runVerifier(patch)

      assertTrue(outcome.isAccepted(patch))
      assertEquals(listOf(file), outcome.verifier.writableFiles)
    }

    @Test
    fun `an added file under a VCS root outside the project dir is accepted`(): Unit = timeoutRunBlocking {
      val patch = added("../vcs/New.txt")

      val outcome = runVerifier(patch)

      assertTrue(outcome.isAccepted(patch))
      assertTrue(vcsDir.resolve("New.txt").isRegularFile())
    }
  }

  private suspend fun runVerifier(vararg patches: FilePatch, configure: (PathsVerifier) -> Unit = {}): Outcome {
    val verifier = PathsVerifier(project, baseDir, patches.toMutableList())
    configure(verifier)
    val precheckFailed = verifier.nonWriteActionPreCheck()
    val skipped = verifier.skipped.toList()
    val executeFailed = edtWriteAction { verifier.execute() }
    return Outcome(verifier, precheckFailed, skipped, executeFailed)
  }

  private suspend fun applyMoves(outcome: Outcome) {
    val verifier = outcome.verifier
    withContext(Dispatchers.EDT) {
      for (patchAndFile in verifier.textPatches + verifier.binaryPatches) {
        verifier.doMoveIfNeeded(patchAndFile.file)
      }
    }
  }

  /**
   * Returns enough `../` segments to go above the file system root from the project dir.
   */
  private fun climbAboveFileSystemRoot(): String = "../".repeat(projectDir.nameCount + 3)

  private class Outcome(
    val verifier: PathsVerifier,
    val precheckFailed: List<FilePatch>,
    val skipped: List<FilePatch>,
    val executeFailed: List<FilePatch>,
  ) {
    fun isRejected(patch: FilePatch): Boolean = patch in precheckFailed || patch in executeFailed

    fun isAccepted(patch: FilePatch): Boolean = !isRejected(patch) && patch !in skipped
  }
}

private const val DEEP_DIR_NAME = "paths-verifier-test-above-root"

/** A name from the default list of ignored files of [FileTypeManager]. */
private const val IGNORED_DIR_NAME = ".git"

/**
 * Calls the overwrite prompt of [helper] that [PathsVerifier] uses, in an `every` or a `verify` block.
 * [files] matches the files of the prompt. Each other argument matches any value.
 */
private fun MockKMatcherScope.overwritePrompt(helper: AbstractVcsHelper, files: List<FilePath> = any()): Collection<FilePath>? =
  helper.selectFilePathsToProcess(files, any(), any(), any(), any(), any(), any(), any())

private fun TestFixture<Path>.childDirFixture(name: String): TestFixture<Path> = testFixture(name) {
  val dir = init().resolve(name).createDirectories()
  initialized(dir) {
    // The parent fixture deletes the directory.
  }
}

private fun modified(path: String): TextFilePatch = textPatch(before = path, after = path, status = null)

private fun added(path: String): TextFilePatch = textPatch(before = null, after = path, status = FileStatus.ADDED)

private fun deleted(path: String): TextFilePatch = textPatch(before = path, after = null, status = FileStatus.DELETED)

private fun moved(from: String, to: String): TextFilePatch = textPatch(before = from, after = to, status = null)

private fun textPatch(before: String?, after: String?, status: FileStatus?): TextFilePatch {
  return TextFilePatch(StandardCharsets.UTF_8).apply {
    beforeName = before
    afterName = after
    setFileStatus(status)
  }
}

private fun writeFile(path: Path, text: String = "content"): VirtualFile {
  path.parent.createDirectories()
  path.writeText(text)
  return findFile(path)
}

private fun findFile(path: Path): VirtualFile {
  return checkNotNull(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path)) { "Cannot find $path" }
}

private fun systemIndependent(path: Path): String = path.toString().replace('\\', '/')

private fun fileNames(dir: Path): List<String> = dir.listDirectoryEntries().map { it.fileName.toString() }.sorted()
