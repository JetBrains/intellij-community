// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.impl.local.windows

import com.intellij.testFramework.UsefulTestCase.IS_UNDER_TEAMCITY
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import org.junit.jupiter.api.io.TempDir
import java.io.IOException
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.NotDirectoryException
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import kotlin.io.path.createDirectory
import kotlin.io.path.createFile
import kotlin.io.path.div
import kotlin.io.path.writeText

/**
 * Tests the native kernel32/ntdll directory enumeration in [WindowsBufferedDirectoryStream]: opening and
 * releasing a directory handle, enumerating entries, and detecting symbolic links. Windows-only; each test
 * uses its own auto-deleted [TempDir].
 */
@EnabledOnOs(OS.WINDOWS)
class WindowsBufferedDirectoryStreamTest {
  /** Reads the whole directory through the native iterator into a (file name -> attributes) map. */
  private fun listNative(directory: Path): Map<String, BasicFileAttributes> {
    val result = HashMap<String, BasicFileAttributes>()
    WindowsBufferedDirectoryStream(directory).use { stream ->
      for (entry in stream) {
        result[entry.first.fileName.toString()] = entry.second
      }
    }
    return result
  }

  @Test
  fun apiStartsAndOpensDirectoryHandle(@TempDir tempDir: Path) {
    // Constructing the iterator triggers the kernel32/ntdll library lookup and a CreateFileW call.
    val iterator = WindowsBufferedDirectoryIterator(tempDir)
    try {
      val handleAddress = iterator.directoryHandle.address()
      assertNotEquals(0L, handleAddress, "CreateFileW must not return a NULL handle for an existing directory")
      assertNotEquals(-1L, handleAddress, "CreateFileW must not return INVALID_HANDLE_VALUE for an existing directory")
    }
    finally {
      iterator.close()
    }
  }

  @Test
  fun handleIsReleasedOnClose(@TempDir tempDir: Path) {
    val iterator = WindowsBufferedDirectoryIterator(tempDir)
    try {
      assertNotEquals(0L, iterator.directoryHandle.address(), "Precondition: a valid handle must be open")

      iterator.closeCurrentDirectoryHandleIfOpen()
      assertEquals(0L, iterator.directoryHandle.address(), "Handle field must be reset to NULL after CloseHandle")

      // Releasing an already-released handle must be a safe no-op.
      iterator.closeCurrentDirectoryHandleIfOpen()
      assertEquals(0L, iterator.directoryHandle.address())
    }
    finally {
      iterator.close()
    }
  }

  @Test
  fun enumeratesCreatedFilesAndSkipsDotEntries(@TempDir tempDir: Path) {
    val fileNames = setOf("alpha.txt", "beta.bin", "gamma")
    for (name in fileNames) (tempDir / name).writeText("content of $name")
    (tempDir / "nested").createDirectory()

    val listed = listNative(tempDir)

    assertEquals(fileNames + "nested", listed.keys, "Enumeration must return exactly the created entries")
    assertFalse(listed.containsKey("."), "The '.' entry must be skipped")
    assertFalse(listed.containsKey(".."), "The '..' entry must be skipped")
    assertTrue(listed.getValue("nested").isDirectory, "'nested' must be reported as a directory")
    assertTrue(listed.getValue("alpha.txt").isRegularFile, "'alpha.txt' must be reported as a regular file")
    assertEquals(Files.size(tempDir / "alpha.txt"), listed.getValue("alpha.txt").size(), "Reported size must match the file size")
  }

  // Symlink probing is exercised locally; TeamCity agents lack the privilege/Developer Mode to create them.
  @Test
  @Disabled("RIDER-139688")
  fun detectsSymbolicLinks(@TempDir tempDir: Path) {
    val target = (tempDir / "target.txt").apply { writeText("payload") }
    (tempDir / "plain.txt").writeText("not a link")
    (tempDir / "dir").createDirectory()

    val link = tempDir / "link.txt"
    try {
      Files.createSymbolicLink(link, target)
    }
    catch (e: IOException) {
      // Creating symbolic links on Windows requires SeCreateSymbolicLinkPrivilege (Developer Mode or elevation).
      Assumptions.assumeTrue(false) { "Cannot create symbolic links on this machine: ${e.message}" }
    }

    val listed = listNative(tempDir)

    assertTrue(listed.getValue("link.txt").isSymbolicLink, "A symlink must be detected via FSCTL_GET_REPARSE_POINT")
    assertFalse(listed.getValue("plain.txt").isSymbolicLink, "A regular file must not be reported as a symlink")
    assertFalse(listed.getValue("target.txt").isSymbolicLink, "The symlink target must not be reported as a symlink")
    assertFalse(listed.getValue("dir").isSymbolicLink, "A plain directory must not be reported as a symlink")
  }
}

class WindowsBufferedDirectoryStreamTestExceptions {

  @BeforeEach
  fun configureNotRethrow() {
    System.setProperty("intellij.testFramework.rethrow.logged.errors", "true")
  }

  @AfterEach
  fun configureRethrow() {
    System.setProperty("intellij.testFramework.rethrow.logged.errors", "false")
  }

  @Test
  fun throwsWhenDirectoryHandleCannotBeOpened(@TempDir tempDir: Path) {
    // CreateFileW returns INVALID_HANDLE_VALUE (-1) for a path that does not exist;
    // this translates to a NIO NoSuchFileException.
    val missing = tempDir / "does-not-exist"
    assertThrows<NoSuchFileException> { WindowsBufferedDirectoryIterator(missing) }
  }

  @Test
  fun throwsWhenTryingToOpenFileAsDirectory(@TempDir tempDir: Path) {
    // and here it must throw NotDirectoryException
    val materializedFile = (tempDir / "materialized.txt").createFile()
    assertThrows<NotDirectoryException> { WindowsBufferedDirectoryIterator(materializedFile) }
  }

  @Test
  fun nextWithoutHasNextReturnsEveryEntryThenThrows(@TempDir tempDir: Path) {
    // Driving the iterator through next() alone must yield each entry once, then NoSuchElementException.
    val names = setOf("a", "b", "c")
    for (name in names) (tempDir / name).writeText(name)

    WindowsBufferedDirectoryIterator(tempDir).use { iter ->
      val collected = HashSet<String>()
      repeat(names.size) { collected += iter.next().first.fileName.toString() }
      assertEquals(names, collected, "next() must return each entry once without a preceding hasNext()")
      assertThrows<NoSuchElementException> { iter.next() }
    }
  }

  @Test
  fun repeatedHasNextIsIdempotent(@TempDir tempDir: Path) {
    // hasNext() must not consume or skip entries when called several times in a row.
    (tempDir / "only").writeText("x")

    WindowsBufferedDirectoryIterator(tempDir).use { iter ->
      assertTrue(iter.hasNext())
      assertTrue(iter.hasNext())
      assertTrue(iter.hasNext())
      assertEquals("only", iter.next().first.fileName.toString(), "Repeated hasNext() must not advance past the entry")
      assertFalse(iter.hasNext(), "hasNext() must report exhaustion once the single entry is consumed")
      assertFalse(iter.hasNext(), "hasNext() must stay false after exhaustion")
      assertThrows<NoSuchElementException> { iter.next() }
    }
  }

  @Test
  fun emptyDirectoryHasNoEntries(@TempDir tempDir: Path) {
    // The '.'/'..' entries are skipped, so an empty directory must be reported as having no elements.
    WindowsBufferedDirectoryIterator(tempDir).use { iter ->
      assertFalse(iter.hasNext(), "An empty directory must have no enumerable entries")
      assertThrows<NoSuchElementException> { iter.next() }
    }
  }
}
