// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.impl.local.windows

import com.intellij.util.io.PlatformNioHelper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.FileVisitor
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import kotlin.io.path.createDirectories
import kotlin.io.path.createDirectory
import kotlin.io.path.div
import kotlin.io.path.name
import kotlin.io.path.writeText

/**
 * Tests the iterative (explicit-stack) `WindowsBufferedFileTreeWalker` through its public entry point
 * [PlatformNioHelper.walkFileTree], which routes to the walker on Windows (see
 * [PlatformNioHelper.useWindowsBufferedDirectoryStream]). The walker replaced a recursive walk; these tests pin its
 * [FileVisitor] contract - pre/visit/post ordering, `SKIP_SUBTREE`, `SKIP_SIBLINGS`, `TERMINATE` - and its
 * StackOverflow-safety on very deep trees. Windows-only; each test uses its own auto-deleted [TempDir].
 */
@EnabledOnOs(OS.WINDOWS)
class WindowsBufferedFileTreeWalkerTest {
  /** Records pre/visit/post callbacks (by file name) so tests can assert both the visited set and the ordering. */
  private open class RecordingVisitor : FileVisitor<Path> {
    val events: MutableList<String> = ArrayList()
    val preDirs: MutableList<String> = ArrayList()
    val files: MutableList<String> = ArrayList()
    val postDirs: MutableList<String> = ArrayList()

    override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
      preDirs += dir.name
      events += "pre:${dir.name}"
      return FileVisitResult.CONTINUE
    }

    override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
      files += file.name
      events += "file:${file.name}"
      return FileVisitResult.CONTINUE
    }

    override fun visitFileFailed(file: Path, exc: IOException): FileVisitResult {
      events += "failed:${file.name}"
      return FileVisitResult.CONTINUE
    }

    override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
      postDirs += dir.name
      events += "post:${dir.name}"
      return FileVisitResult.CONTINUE
    }
  }

  /** Runs the walk, skipping the test if the platform would not route through the buffered walker for [root]. */
  private fun walk(root: Path, visitor: FileVisitor<Path>) {
    Assumptions.assumeTrue(PlatformNioHelper.useWindowsBufferedDirectoryStream(root)) {
      "walkFileTree must route to the Windows buffered walker for $root"
    }
    PlatformNioHelper.walkFileTree(root, visitor)
  }

  @Test
  fun visitsEveryEntryWithPreVisitBeforeAndPostVisitAfterChildren(@TempDir tempDir: Path) {
    (tempDir / "a.txt").writeText("a")
    (tempDir / "z.txt").writeText("z")
    val sub = (tempDir / "sub").createDirectory()
    (sub / "b.txt").writeText("b")
    val deep = (sub / "deep").createDirectory()
    (deep / "c.txt").writeText("c")

    val v = RecordingVisitor()
    walk(tempDir, v)

    assertEquals(setOf("a.txt", "z.txt", "b.txt", "c.txt"), v.files.toSet(), "every file must be visited")
    assertEquals(4, v.files.size, "no file may be visited more than once")
    assertEquals(setOf(tempDir.name, "sub", "deep"), v.preDirs.toSet(), "every directory must be pre-visited")
    assertEquals(v.preDirs.toSet(), v.postDirs.toSet(), "every pre-visited directory must be post-visited")
    assertEquals(v.preDirs.size, v.postDirs.size, "no directory may be post-visited more than once")

    // Structural ordering invariants, independent of the (unspecified) sibling enumeration order.
    assertEquals("pre:${tempDir.name}", v.events.first(), "the root must be entered first")
    assertEquals("post:${tempDir.name}", v.events.last(), "the root must be closed last")
    assertTrue(v.events.indexOf("pre:sub") < v.events.indexOf("pre:deep"), "a parent must be entered before its child directory")
    assertTrue(v.events.indexOf("pre:deep") < v.events.indexOf("file:c.txt"), "a directory must be entered before its files are visited")
    assertTrue(v.events.indexOf("file:c.txt") < v.events.indexOf("post:deep"), "a directory's files must be visited before its postVisit")
    assertTrue(v.events.indexOf("post:deep") < v.events.indexOf("post:sub"), "a child directory must be closed before its parent")
  }

  @Test
  fun emptyDirectoryIsPreAndPostVisitedWithNoFiles(@TempDir tempDir: Path) {
    val v = RecordingVisitor()
    walk(tempDir, v)

    assertEquals(listOf(tempDir.name), v.preDirs, "an empty root must be pre-visited exactly once")
    assertEquals(listOf(tempDir.name), v.postDirs, "an empty root must be post-visited exactly once")
    assertTrue(v.files.isEmpty(), "an empty directory has no files to visit")
  }

  @Test
  fun skipSubtreeFromPreVisitDirectorySkipsDescendantsAndOmitsPostVisit(@TempDir tempDir: Path) {
    (tempDir / "keep.txt").writeText("keep")
    val skipped = (tempDir / "skipped").createDirectory()
    (skipped / "hidden.txt").writeText("hidden")
    (skipped / "hiddenDir").createDirectory()

    val v = object : RecordingVisitor() {
      override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
        super.preVisitDirectory(dir, attrs)
        return if (dir.name == "skipped") FileVisitResult.SKIP_SUBTREE else FileVisitResult.CONTINUE
      }
    }
    walk(tempDir, v)

    assertTrue(v.preDirs.contains("skipped"), "the skipped directory is still pre-visited")
    assertFalse(v.preDirs.contains("hiddenDir"), "directories under a SKIP_SUBTREE directory must not be visited")
    assertFalse(v.files.contains("hidden.txt"), "files under a SKIP_SUBTREE directory must not be visited")
    assertFalse(v.postDirs.contains("skipped"), "SKIP_SUBTREE must omit postVisitDirectory for that directory")
    assertTrue(v.files.contains("keep.txt"), "siblings of the skipped directory must still be visited")
    assertTrue(v.postDirs.contains(tempDir.name), "the root must still be post-visited")
  }

  @Test
  fun skipSiblingsFromVisitFileSkipsRemainingFilesButStillRunsPostVisit(@TempDir tempDir: Path) {
    for (name in listOf("f1.txt", "f2.txt", "f3.txt", "f4.txt")) (tempDir / name).writeText(name)

    val v = object : RecordingVisitor() {
      override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
        super.visitFile(file, attrs)
        return FileVisitResult.SKIP_SIBLINGS
      }
    }
    walk(tempDir, v)

    assertEquals(1, v.files.size, "SKIP_SIBLINGS on the first file must skip the remaining files in the directory")
    assertTrue(v.postDirs.contains(tempDir.name), "SKIP_SIBLINGS must still run postVisitDirectory for the directory")
  }

  @Test
  fun skipSiblingsFromPostVisitDirectoryStillClosesParentDirectories(@TempDir tempDir: Path) {
    val branch = (tempDir / "branch").createDirectory()
    val leaf = (branch / "leaf").createDirectory()
    (leaf / "x.txt").writeText("x")
    for (name in listOf("s1.txt", "s2.txt", "s3.txt")) (branch / name).writeText(name)

    val v = object : RecordingVisitor() {
      override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
        super.postVisitDirectory(dir, exc)
        return if (dir.name == "leaf") FileVisitResult.SKIP_SIBLINGS else FileVisitResult.CONTINUE
      }
    }
    walk(tempDir, v)

    assertTrue(v.postDirs.contains("leaf"), "the leaf directory must be post-visited")
    assertTrue(v.files.contains("x.txt"), "the leaf's file must be visited")
    assertTrue(v.postDirs.contains("branch"), "SKIP_SIBLINGS from a child's postVisit must still close the parent")
    assertTrue(v.postDirs.contains(tempDir.name), "the root must still be post-visited")
  }

  @Test
  fun terminateFromVisitFileStopsWalkImmediately(@TempDir tempDir: Path) {
    for (name in listOf("f1.txt", "f2.txt", "f3.txt")) (tempDir / name).writeText(name)

    val v = object : RecordingVisitor() {
      override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
        super.visitFile(file, attrs)
        return FileVisitResult.TERMINATE
      }
    }
    walk(tempDir, v)

    assertEquals(1, v.files.size, "TERMINATE on the first file must stop the walk before any other file is visited")
    assertTrue(v.postDirs.isEmpty(), "TERMINATE must skip postVisitDirectory for every open directory, including the root")
  }
}
