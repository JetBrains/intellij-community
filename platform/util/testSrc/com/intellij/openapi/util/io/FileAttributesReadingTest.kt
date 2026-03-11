// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util.io

import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.text.StringUtil
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.util.SystemProperties
import com.intellij.util.TimeoutUtil
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.DosFileAttributeView
import java.nio.file.attribute.FileTime
import java.nio.file.attribute.PosixFilePermissions
import java.time.Instant
import java.util.function.Consumer
import kotlin.io.path.absolutePathString
import kotlin.math.max
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FileAttributesReadingTest {
  private val tempDirFixture = tempPathFixture()
  private val tempDir: Path
    get() = tempDirFixture.get()

  private val myTestData = byteArrayOf('t'.code.toByte(), 'e'.code.toByte(), 's'.code.toByte(), 't'.code.toByte())

  @Test
  fun winReparsePointAttributeConversion() {
    IoTestUtil.assumeWindows()

    val nioAttributes = object : BasicFileAttributes {
      override fun lastModifiedTime(): FileTime {
        return FileTime.from(Instant.now())
      }

      override fun lastAccessTime(): FileTime {
        return lastModifiedTime()
      }

      override fun creationTime(): FileTime {
        return lastModifiedTime()
      }

      override fun isRegularFile(): Boolean {
        return false
      }

      override fun isDirectory(): Boolean {
        return true
      }

      override fun isSymbolicLink(): Boolean {
        return false
      }

      override fun isOther(): Boolean {
        return true
      }

      override fun size(): Long {
        return 0
      }

      override fun fileKey(): Any? {
        return null
      }
    }

    val rootAttributes = FileAttributes.fromNio(Path.of(System.getenv("SystemDrive") + '\\'), nioAttributes)
    assertTrue(rootAttributes.isDirectory)
    assertEquals(FileAttributes.Type.DIRECTORY, rootAttributes.getType())
    assertFalse(rootAttributes.isSymLink)

    val dirAttributes = FileAttributes.fromNio(Path.of(System.getenv("USERPROFILE")), nioAttributes)
    assertTrue(dirAttributes.isDirectory)
    assertEquals(FileAttributes.Type.DIRECTORY, dirAttributes.getType())
    assertTrue(dirAttributes.isSymLink)
  }

  @Test
  fun missingFile() {
    val file = tempDir.resolve("missing.txt").toFile()
    assertFalse(file.exists())
    val attributes = getAttributes(file.path)
    assertNull(attributes)

    val target = resolveSymLink(file)
    assertNull(target)
  }

  @Test
  fun regularFile() {
    val path = tempDir.resolve("file.txt")
    Files.write(path, myTestData)

    assertFileAttributes(path.toFile())

    val target = resolveSymLink(path.toFile())
    assertEquals(path.toFile().path, target)
  }

  @Test
  fun readOnlyFile() {
    val file = tempDir.newFile("file.txt")
    NioFiles.setReadOnly(file.toPath(), true)
    val attributes = getAttributes(file)
    assertEquals(FileAttributes.Type.FILE, attributes.getType())
    assertFalse(attributes.isWritable)
  }

  @Test
  fun directory() {
    val file = tempDir.newDirectory("dir")

    val attributes = getAttributes(file)
    assertEquals(FileAttributes.Type.DIRECTORY, attributes.getType())
    assertFalse(attributes.isSymLink)
    assertFalse(attributes.isHidden)
    assertTrue(attributes.isWritable)
    assertEquals(
      0, attributes.length,
      "directory.length() is defined to be 0",
    )
    assertEquals(file.lastModified(), attributes.lastModified)
    assertTrue(attributes.isWritable)

    val target = resolveSymLink(file)
    assertEquals(file.path, target)
  }

  @Test
  fun readOnlyDirectory() {
    val dir = tempDir.newDirectory("dir")
    NioFiles.setReadOnly(dir.toPath(), true)
    val attributes = getAttributes(dir)
    assertEquals(FileAttributes.Type.DIRECTORY, attributes.getType())
    assertTrue(attributes.isWritable)
  }

  @Test
  fun root() {
    val file = File(if (SystemInfo.isWindows) "C:\\" else "/")

    val attributes = getAttributes(file)
    assertEquals(FileAttributes.Type.DIRECTORY, attributes.getType(), "$file $attributes")
    assertFalse(attributes.isSymLink, "$file $attributes")
  }

  @Test
  fun badNames() {
    val file = tempDir.newFile("file.txt")
    Files.write(file.toPath(), myTestData)

    assertFileAttributes(File(file.path + StringUtil.repeat(File.separator, 3)))
    assertFileAttributes(File(file.path.replace(File.separator, StringUtil.repeat(File.separator, 3))))
    assertFileAttributes(File(file.path.replace(File.separator, File.separator + "." + File.separator)))
    assertFileAttributes(
      File(tempDir.toFile(), File.separator + ".." + File.separator + tempDir.toFile().getName() + File.separator + file.getName())
    )

    if (SystemInfo.isUnix) {
      val backSlashFile = tempDir.newFile("file\\txt")
      Files.write(backSlashFile.toPath(), myTestData)
      assertFileAttributes(backSlashFile)
    }
  }

  @Test
  fun special() {
    IoTestUtil.assumeUnix()
    val file = File("/dev/null")

    val attributes = getAttributes(file)
    assertEquals(FileAttributes.Type.SPECIAL, attributes.getType())
    assertFalse(attributes.isSymLink)
    assertFalse(attributes.isHidden)
    assertTrue(attributes.isWritable)
    assertEquals(0, attributes.length)
    assertTrue(attributes.isWritable)

    val target = resolveSymLink(file)
    assertEquals(file.path, target)
  }

  @Test
  fun linkToFile() {
    IoTestUtil.assumeSymLinkCreationIsSupported()

    val file = tempDir.newFile("file.txt")
    Files.write(file.toPath(), myTestData)
    assertTrue(file.setLastModified(file.lastModified() - 5000))
    assertTrue(file.setWritable(false, false))
    val link = tempDir.resolve("link").toFile()
    Files.createSymbolicLink(link.toPath(), file.toPath())

    val attributes = getAttributes(link)
    assertEquals(FileAttributes.Type.FILE, attributes.getType())
    assertTrue(attributes.isSymLink)
    assertFalse(attributes.isHidden)
    assertFalse(attributes.isWritable)

    assertEquals(myTestData.size.toLong(), attributes.length)
    assertEquals(file.lastModified(), attributes.lastModified)
    assertFalse(attributes.isWritable)

    val target = resolveSymLink(link)
    assertEquals(file.path, target)
  }

  @Test
  fun doubleLink() {
    IoTestUtil.assumeSymLinkCreationIsSupported()

    val file = tempDir.newFile("file.txt")
    Files.write(file.toPath(), myTestData)
    assertTrue(file.setLastModified(file.lastModified() - 5000))
    assertTrue(file.setWritable(false, false))
    val link1 = tempDir.resolve("link1").toFile()
    Files.createSymbolicLink(link1.toPath(), file.toPath())
    val link2 = tempDir.resolve("link2").toFile()
    Files.createSymbolicLink(link2.toPath(), link1.toPath())

    val attributes = getAttributes(link2)
    assertEquals(FileAttributes.Type.FILE, attributes.getType())
    assertTrue(attributes.isSymLink)
    assertFalse(attributes.isHidden)
    assertFalse(attributes.isWritable)

    assertEquals(myTestData.size.toLong(), attributes.length)
    assertEquals(file.lastModified(), attributes.lastModified)
    assertFalse(attributes.isWritable)

    val target = resolveSymLink(link2)
    assertEquals(file.path, target)
  }

  @Test
  fun linkToDirectory() {
    IoTestUtil.assumeSymLinkCreationIsSupported()

    val dir = tempDir.newDirectory("dir")
    if (SystemInfo.isUnix) assertTrue(dir.setWritable(false, false))
    assertTrue(dir.setLastModified(dir.lastModified() - 5000))
    val link = tempDir.resolve("link").toFile()
    Files.createSymbolicLink(link.toPath(), dir.toPath())

    val attributes = getAttributes(link)
    assertEquals(FileAttributes.Type.DIRECTORY, attributes.getType())
    assertTrue(attributes.isSymLink)
    assertFalse(attributes.isHidden)
    assertTrue(attributes.isWritable)
    assertEquals(
      0, attributes.length,
      "directory.length() is defined to be 0",
    )
    assertEquals(dir.lastModified(), attributes.lastModified)

    val target = resolveSymLink(link)
    assertEquals(dir.path, target)
  }

  @Test
  fun missingLink() {
    IoTestUtil.assumeSymLinkCreationIsSupported()

    val file = tempDir.resolve("file.txt").toFile()
    assertFalse(file.exists())
    val link = tempDir.resolve("link").toFile()
    assertFalse(link.exists())
    val link1 = link.toPath()
    val target1 = file.toPath()
    Files.createSymbolicLink(link1, target1)

    val attributes = getAttributes(link)
    assertNull(attributes.getType())
    assertTrue(attributes.isSymLink)
    assertFalse(attributes.isHidden)
    assertTrue(attributes.isWritable)
    assertEquals(0, attributes.length)

    val target = resolveSymLink(link)
    assertNull(target, target)
  }

  @Test
  fun selfLink() {
    IoTestUtil.assumeSymLinkCreationIsSupported()

    val link = tempDir.resolve("self_link").toFile()
    val link1 = link.toPath()
    val target = link.toPath()
    Files.createSymbolicLink(link1, target)

    val attributes = getAttributes(link)
    assertNull(attributes.getType())
    assertTrue(attributes.isSymLink)
    assertFalse(attributes.isHidden)
    assertTrue(attributes.isWritable)
    assertEquals(0, attributes.lastModified)
    assertNull(resolveSymLink(link))
  }

  @Test
  fun innerSymlinkResolve() {
    IoTestUtil.assumeSymLinkCreationIsSupported()

    val file = tempDir.newFile("dir/file.txt")
    val link = tempDir.resolve("link").toFile()
    val link1 = link.toPath()
    val target1 = file.getParentFile().toPath()
    Files.createSymbolicLink(link1, target1)

    val target = resolveSymLink(File(link.path + '/' + file.getName()))
    assertEquals(file.path, target)
  }

  @Test
  fun junction() {
    IoTestUtil.assumeWindows()

    val target = tempDir.newDirectory("dir")
    val junction = IoTestUtil.createJunction(target.path, tempDir.toFile().toString() + "/junction.dir")

    try {
      var attributes = getAttributes(junction)
      assertEquals(FileAttributes.Type.DIRECTORY, attributes.getType())
      assertTrue(attributes.isSymLink)
      assertFalse(attributes.isHidden)
      assertTrue(attributes.isWritable)

      val resolved1 = resolveSymLink(junction)
      assertEquals(target.path, resolved1)

      Files.delete(target.toPath())

      attributes = getAttributes(junction)
      assertNull(attributes.getType())
      assertTrue(attributes.isSymLink)
      assertFalse(attributes.isHidden)
      assertTrue(attributes.isWritable)

      val resolved2 = resolveSymLink(junction)
      assertNull(resolved2)
    }
    finally {
      IoTestUtil.deleteJunction(junction.path)
    }
  }

  @Test
  fun innerJunctionResolve() {
    IoTestUtil.assumeWindows()

    val file = tempDir.newFile("dir/file.txt")
    val junction = tempDir.resolve("junction").toFile()
    IoTestUtil.createJunction(file.getParent(), junction.path)

    val target = resolveSymLink(File(junction.path + '/' + file.getName()))
    assertEquals(file.path, target)
  }

  @Test
  fun hiddenDir() {
    IoTestUtil.assumeWindows()
    val dir = tempDir.newDirectory("dir")
    var attributes = getAttributes(dir)
    assertFalse(attributes.isHidden)
    Files.getFileAttributeView(dir.toPath(), DosFileAttributeView::class.java).setHidden(true)
    attributes = getAttributes(dir)
    assertTrue(attributes.isHidden)
  }

  @Test
  fun hiddenFile() {
    IoTestUtil.assumeWindows()
    val file = tempDir.newFile("file")
    var attributes = getAttributes(file)
    assertFalse(attributes.isHidden)
    Files.getFileAttributeView(file.toPath(), DosFileAttributeView::class.java).setHidden(true)
    attributes = getAttributes(file)
    assertTrue(attributes.isHidden)
  }

  @Test
  fun notSoHiddenRoot() {
    val absRoot = if (SystemInfo.isWindows) File(System.getenv("SystemDrive") + '\\') else File("/")
    val absAttributes = getAttributes(absRoot)
    assertFalse(absAttributes.isHidden)
  }

  @Test
  fun wellHiddenFile() {
    IoTestUtil.assumeWindows()
    val file = File("C:\\Documents and Settings\\desktop.ini")
    Assumptions.assumeTrue(file.exists(), "$file is not there")

    val attributes = getAttributes(file)
    assertEquals(FileAttributes.Type.FILE, attributes.getType())
    assertFalse(attributes.isSymLink)
    assertTrue(attributes.isHidden)
    assertTrue(attributes.isWritable)
    assertEquals(file.length(), attributes.length)
    assertEquals(file.lastModified(), attributes.lastModified)
  }

  @Test
  fun extraLongName() {
    val prefix = StringUtil.repeatSymbol('a', 128) + "."
    var file = tempDir.newFile("$prefix.dir/$prefix.dir/$prefix.dir/$prefix.dir/$prefix.dir/$prefix.txt")
    Files.write(file.toPath(), myTestData)

    assertFileAttributes(file)

    var target = resolveSymLink(file)
    assertEquals(file.path, target)

    if (SystemInfo.isWindows) {
      val path = StringBuilder(tempDir.toFile().path)
      val length = 250 - path.length
      path.append("\\x_x_x_x_x".repeat(max(0, length / 10)))

      val baseDir = File(path.toString())
      assertTrue(baseDir.mkdirs())
      assertTrue(getAttributes(baseDir).isDirectory)

      for (i in 1..100) {
        val dir = File(baseDir, StringUtil.repeat("x", i))
        assertTrue(dir.mkdir())
        assertTrue(getAttributes(dir).isDirectory)

        file = File(dir, "file.txt")
        Files.write(file.toPath(), myTestData)
        assertTrue(file.exists())
        assertFileAttributes(file)

        target = resolveSymLink(file)
        assertEquals(file.path, target)
      }
    }
  }

  @Test
  fun subst() {
    IoTestUtil.assumeWindows()

    tempDir.newFile("file.txt") // just to populate a directory
    IoTestUtil.performTestOnWindowsSubst(tempDir.root.absolutePathString(), Consumer { substRoot: File? ->
      val attributes = getAttributes(substRoot!!)
      assertEquals(FileAttributes.Type.DIRECTORY, attributes.getType(), "$substRoot $attributes")
      assertFalse(attributes.isSymLink, "$substRoot $attributes")

      val children = substRoot.listFiles()
      assertNotNull(children)
      assertEquals(1, children.size.toLong())
      val file = children[0]
      val target = resolveSymLink(file)
      assertEquals(file.path, target)
    })
  }

  @Test
  fun hardLink() {
    IoTestUtil.assumeSymLinkCreationIsSupported()
    val target = tempDir.newFile("file.txt")
    val link = tempDir.resolve("link")
    Files.createLink(link, target.toPath())
    val linkFile = link.toFile()

    var attributes = getAttributes(linkFile)
    assertEquals(FileAttributes.Type.FILE, attributes.getType())
    assertEquals(target.length(), attributes.length)
    assertEquals(target.lastModified(), attributes.lastModified)

    Files.write(target.toPath(), myTestData)
    assertTrue(target.setLastModified(attributes.lastModified - 5000))
    assertTrue(target.length() > 0)
    assertEquals(attributes.lastModified - 5000, target.lastModified())

    if (SystemInfo.isWindows) {
      val bytes = Files.readAllBytes(linkFile.toPath())
      assertEquals(myTestData.size.toLong(), bytes.size.toLong())
    }

    attributes = getAttributes(linkFile)
    assertEquals(FileAttributes.Type.FILE, attributes.getType())
    assertEquals(target.length(), attributes.length)
    assertEquals(target.lastModified(), attributes.lastModified)

    val resolved = resolveSymLink(linkFile)
    assertEquals(linkFile.path, resolved)
  }

  @Test
  fun stamps() {
    var attributes = getAttributes(tempDir.toFile())
    Assumptions.assumeTrue(
      attributes.lastModified > attributes.lastModified / 1000 * 1000,
      "expected FS has millisecond resolution but got lastModified: " + attributes.lastModified
    )

    var t1 = System.currentTimeMillis()
    TimeoutUtil.sleep(10)
    val file = tempDir.newFile("test.txt")
    TimeoutUtil.sleep(10)
    var t2 = System.currentTimeMillis()
    attributes = getAttributes(file)
    assertThat(attributes.lastModified).isBetween(t1, t2)

    t1 = System.currentTimeMillis()
    TimeoutUtil.sleep(10)
    Files.write(file.toPath(), myTestData)
    TimeoutUtil.sleep(10)
    t2 = System.currentTimeMillis()
    attributes = getAttributes(file)
    assertThat(attributes.lastModified).isBetween(t1, t2)

    val cmd = if (SystemInfo.isWindows)
      ProcessBuilder("attrib", "-A", file.path)
    else
      ProcessBuilder("chmod", "644", file.path)
    assertEquals(0, cmd.start().waitFor().toLong())
    attributes = getAttributes(file)
    assertThat(attributes.lastModified).isBetween(t1, t2)
  }

  @Test
  fun notOwned() {
    val userHome = File(SystemProperties.getUserHome())

    val homeAttributes = getAttributes(userHome)
    assertTrue(homeAttributes.isDirectory)
    assertTrue(homeAttributes.isWritable)

    val parentAttributes = getAttributes(userHome.getParentFile())
    assertTrue(parentAttributes.isDirectory)
    assertTrue(parentAttributes.isWritable)

    if (SystemInfo.isUnix) {
      val mutantFile = tempDir.newFile("mutant")
      Files.setPosixFilePermissions(mutantFile.toPath(), PosixFilePermissions.fromString("r--rw-rw-"))
      val mutantAttrs = getAttributes(mutantFile)
      assertTrue(mutantAttrs.isFile)
      assertFalse(mutantAttrs.isWritable)

      val devNull = getAttributes(File("/dev/null"))
      assertTrue(devNull.isSpecial)
      assertTrue(devNull.isWritable)

      val etcPasswd = getAttributes(File("/etc/passwd"))
      assertTrue(etcPasswd.isFile)
      assertFalse(etcPasswd.isWritable)
    }
  }

  @Test
  fun unicodeName() {
    val name = IoTestUtil.getUnicodeName()
    Assumptions.assumeTrue(name != null, "Unicode names not supported")
    val file = tempDir.newFile("$name.txt")
    Files.write(file.toPath(), myTestData)

    assertFileAttributes(file)

    val target = resolveSymLink(file)
    assertEquals(file.path, target)
  }

  companion object {
    // Extension methods to minimize diff when creating test files/directories
    private fun Path.newFile(name: String): File {
      val path = resolve(name)
      path.parent?.let { Files.createDirectories(it) }
      Files.createFile(path)
      return path.toFile()
    }

    private fun Path.newDirectory(name: String): File {
      val path = resolve(name)
      Files.createDirectories(path)
      return path.toFile()
    }

    private fun resolveSymLink(file: File): String? {
      val realPath = FileSystemUtil.resolveSymLink(file.absolutePath)
      if (realPath != null && (SystemInfo.isWindows && realPath.startsWith("\\\\") || File(realPath).exists())) {
        return realPath
      }
      return null
    }

    private fun getAttributes(file: File): FileAttributes {
      val path = file.path
      val attributes = getAttributes(path)
      assertNotNull(attributes, path + ", exists=" + file.exists())
      return attributes
    }

    private fun getAttributes(path: String): FileAttributes? {
      return FileSystemUtil.getAttributes(path)
    }

    private fun assertFileAttributes(file: File) {
      val attributes = getAttributes(file)
      assertEquals(FileAttributes.Type.FILE, attributes.getType())
      assertFalse(attributes.isSymLink)
      assertFalse(attributes.isHidden)
      assertTrue(attributes.isWritable)
      assertEquals(file.length(), attributes.length)
      assertEquals(file.lastModified(), attributes.lastModified)
      assertTrue(attributes.isWritable)
    }
  }
}
