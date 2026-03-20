// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util.io

import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.impl.local.listWithAttributesUsingEel
import com.intellij.openapi.vfs.impl.local.readAttributesUsingEel
import com.intellij.platform.eel.EelOsFamily
import com.intellij.platform.eel.environmentVariables
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.platform.eel.provider.asEelPath
import com.intellij.platform.eel.provider.asNioPath
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.eel.spawnProcess
import com.intellij.platform.testFramework.junit5.eel.params.api.EelHolder
import com.intellij.platform.testFramework.junit5.eel.params.api.TestApplicationWithEel
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.util.TimeoutUtil
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.OS
import org.junit.jupiter.params.ParameterizedClass
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.DosFileAttributeView
import java.nio.file.attribute.FileTime
import java.nio.file.attribute.PosixFilePermissions
import java.time.Instant
import java.util.function.Consumer
import kotlin.io.path.absolutePathString
import kotlin.io.path.name
import kotlin.math.max
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@TestApplicationWithEel(osesMayNotHaveRemoteEels = [OS.WINDOWS, OS.LINUX, OS.MAC])
@ParameterizedClass
class FileAttributesReadingTest(val eelHolder: EelHolder) {
  private val tempDirFixture = tempPathFixture()
  private val tempDir: Path
    get() = tempDirFixture.get()

  private val myTestData = byteArrayOf('t'.code.toByte(), 'e'.code.toByte(), 's'.code.toByte(), 't'.code.toByte())

  @Test
  fun winReparsePointAttributeConversion() {
    assumeWindows()

    // Get environment variables from Eel
    val envVars = runBlocking { eelHolder.eel.exec.environmentVariables().eelIt().await() }
    val systemDrive = envVars["SystemDrive"] ?: "C:"
    val userProfile = envVars["USERPROFILE"] ?: return

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

    val rootAttributes = FileAttributes.fromNio(EelPath.parse(systemDrive + '\\', eelHolder.eel.descriptor).asNioPath(), nioAttributes)
    assertTrue(rootAttributes.isDirectory)
    assertEquals(FileAttributes.Type.DIRECTORY, rootAttributes.getType())
    assertFalse(rootAttributes.isSymLink)

    val dirAttributes = FileAttributes.fromNio(EelPath.parse(userProfile, eelHolder.eel.descriptor).asNioPath(), nioAttributes)
    assertTrue(dirAttributes.isDirectory)
    assertEquals(FileAttributes.Type.DIRECTORY, dirAttributes.getType())
    assertTrue(dirAttributes.isSymLink)
  }

  @Test
  fun missingFile() {
    val path = tempDir.resolve("missing.txt")
    assertFalse(Files.exists(path))
    val attributes = getAttributesNullable(path)
    assertNull(attributes)

    val target = resolveSymLink(path)
    assertNull(target)
  }

  @Test
  fun regularFile() {
    val path = tempDir.resolve("file.txt")
    Files.write(path, myTestData)

    assertFileAttributes(path)

    val target = resolveSymLink(path)
    assertEquals(path.absolutePathString(), target)
  }

  @Test
  fun readOnlyFile() {
    val path = tempDir.newFile("file.txt")
    NioFiles.setReadOnly(path, true)
    val attributes = getAttributes(path)
    assertEquals(FileAttributes.Type.FILE, attributes.getType())
    assertFalse(attributes.isWritable)
  }

  @Test
  fun directory() {
    val path = tempDir.newDirectory("dir")

    val attributes = getAttributes(path)
    assertEquals(FileAttributes.Type.DIRECTORY, attributes.getType())
    assertFalse(attributes.isSymLink)
    assertFalse(attributes.isHidden)
    assertTrue(attributes.isWritable)
    assertEquals(
      0, attributes.length,
      "directory.length() is defined to be 0"
    )
    assertEquals(Files.getLastModifiedTime(path).toMillis(), attributes.lastModified)
    assertTrue(attributes.isWritable)

    val target = resolveSymLink(path)
    assertEquals(path.absolutePathString(), target)
  }

  @Test
  fun readOnlyDirectory() {
    val dir = tempDir.newDirectory("dir")
    NioFiles.setReadOnly(dir, true)
    val attributes = getAttributes(dir)
    assertEquals(FileAttributes.Type.DIRECTORY, attributes.getType())
    if (isLocal) {
      // optimized treating every directory as writable
      assertTrue(attributes.isWritable)
    }
    else {
      assertFalse(attributes.isWritable)
    }
  }

  @Test
  fun root() {
    val path = EelPath.parse(if (isWindows) "C:\\" else "/", eelHolder.eel.descriptor).asNioPath()

    val attributes = getAttributes(path)
    assertEquals(FileAttributes.Type.DIRECTORY, attributes.getType(), "$path $attributes")
    assertFalse(attributes.isSymLink, "$path $attributes")
  }

  @Test
  fun badNames() {
    val path = tempDir.newFile("file.txt")
    Files.write(path, myTestData)

    assertFileAttributes(Path.of(path.toString() + StringUtil.repeat(File.separator, 3)))
    assertFileAttributes(Path.of(path.toString().replace(File.separator, StringUtil.repeat(File.separator, 3))))
    assertFileAttributes(Path.of(path.toString().replace("${File.separator}file.txt", "${File.separator}.${File.separator}file.txt")))
    assertFileAttributes(tempDir.resolve("..").resolve(tempDir.fileName).resolve(path.fileName))

    if (isUnix) {
      val backSlashFile = tempDir.newFile("file\\txt")
      Files.write(backSlashFile, myTestData)
      assertFileAttributes(backSlashFile)
    }
  }

  @Test
  fun special() {
    assumeUnix()
    val path = EelPath.parse("/dev/null", eelHolder.eel.descriptor).asNioPath()

    val attributes = getAttributes(path)
    assertEquals(FileAttributes.Type.SPECIAL, attributes.getType())
    assertFalse(attributes.isSymLink)
    assertFalse(attributes.isHidden)
    assertTrue(attributes.isWritable)
    assertEquals(0, attributes.length)
    assertTrue(attributes.isWritable)

    val target = resolveSymLink(path)
    assertEquals(path.absolutePathString(), target)
  }

  @Test
  fun linkToFile() {
    assumeSymLinkCreationIsSupported()

    val file = tempDir.newFile("file.txt")
    Files.write(file, myTestData)
    val lastModified = Files.getLastModifiedTime(file).toMillis() - 5000
    Files.setLastModifiedTime(file, FileTime.fromMillis(lastModified))
    NioFiles.setReadOnly(file, true)
    val link = tempDir.resolve("link")
    Files.createSymbolicLink(link, file)

    val attributes = getAttributes(link)
    assertEquals(FileAttributes.Type.FILE, attributes.getType())
    assertTrue(attributes.isSymLink)
    assertFalse(attributes.isHidden)
    assertFalse(attributes.isWritable)

    assertEquals(myTestData.size.toLong(), attributes.length)
    assertEquals(Files.getLastModifiedTime(file).toMillis(), attributes.lastModified)
    assertFalse(attributes.isWritable)

    val target = resolveSymLink(link)
    assertEquals(file.absolutePathString(), target)
  }

  @Test
  fun doubleLink() {
    assumeSymLinkCreationIsSupported()

    val file = tempDir.newFile("file.txt")
    Files.write(file, myTestData)
    val lastModified = Files.getLastModifiedTime(file).toMillis() - 5000
    Files.setLastModifiedTime(file, FileTime.fromMillis(lastModified))
    NioFiles.setReadOnly(file, true)
    val link1 = tempDir.resolve("link1")
    Files.createSymbolicLink(link1, file)
    val link2 = tempDir.resolve("link2")
    Files.createSymbolicLink(link2, link1)

    val attributes = getAttributes(link2)
    assertEquals(FileAttributes.Type.FILE, attributes.getType())
    assertTrue(attributes.isSymLink)
    assertFalse(attributes.isHidden)
    assertFalse(attributes.isWritable)

    assertEquals(myTestData.size.toLong(), attributes.length)
    assertEquals(Files.getLastModifiedTime(file).toMillis(), attributes.lastModified)
    assertFalse(attributes.isWritable)

    val target = resolveSymLink(link2)
    assertEquals(file.absolutePathString(), target)
  }

  @Test
  fun linkToDirectory() {
    assumeSymLinkCreationIsSupported()

    val dir = tempDir.newDirectory("dir")
    if (isUnix) NioFiles.setReadOnly(dir, true)
    val lastModified = Files.getLastModifiedTime(dir).toMillis() - 5000
    Files.setLastModifiedTime(dir, FileTime.fromMillis(lastModified))
    val link = tempDir.resolve("link")
    Files.createSymbolicLink(link, dir)

    val attributes = getAttributes(link)
    assertEquals(FileAttributes.Type.DIRECTORY, attributes.getType())
    assertTrue(attributes.isSymLink)
    assertFalse(attributes.isHidden)
    if (isLocal) {
      // optimized treating every directory as writable
      assertTrue(attributes.isWritable)
    }
    else {
      assertEquals(!isUnix, attributes.isWritable)
    }
    assertEquals(0, attributes.length, "directory.length() is defined to be 0")
    assertEquals(Files.getLastModifiedTime(dir).toMillis(), attributes.lastModified)

    val target = resolveSymLink(link)
    assertEquals(dir.absolutePathString(), target)
  }

  @Test
  fun missingLink() {
    assumeSymLinkCreationIsSupported()

    val file = tempDir.resolve("file.txt")
    assertFalse(Files.exists(file))
    val link = tempDir.resolve("link")
    assertFalse(Files.exists(link))
    Files.createSymbolicLink(link, file)

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
    assumeSymLinkCreationIsSupported()

    val link = tempDir.resolve("self_link")
    Files.createSymbolicLink(link, link)

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
    assumeSymLinkCreationIsSupported()

    val file = tempDir.newFile("dir/file.txt")
    val link = tempDir.resolve("link")
    Files.createSymbolicLink(link, file.parent)

    val target = resolveSymLink(link.resolve(file.fileName))
    assertEquals(file.absolutePathString(), target)
  }

  @Test
  fun junction() {
    assumeWindows()

    val target = tempDir.newDirectory("dir")
    val junction = IoTestUtil.createJunction(target.absolutePathString(), "${tempDir}/junction.dir").toPath()

    try {
      var attributes = getAttributes(junction)
      assertEquals(FileAttributes.Type.DIRECTORY, attributes.getType())
      assertTrue(attributes.isSymLink)
      assertFalse(attributes.isHidden)
      assertTrue(attributes.isWritable)

      val resolved1 = resolveSymLink(junction)
      assertEquals(target.absolutePathString(), resolved1)

      Files.delete(target)

      attributes = getAttributes(junction)
      assertNull(attributes.getType())
      assertTrue(attributes.isSymLink)
      assertFalse(attributes.isHidden)
      assertTrue(attributes.isWritable)

      val resolved2 = resolveSymLink(junction)
      assertNull(resolved2)
    }
    finally {
      IoTestUtil.deleteJunction(junction.absolutePathString())
    }
  }

  @Test
  fun innerJunctionResolve() {
    assumeWindows()

    val file = tempDir.newFile("dir/file.txt")
    val junctionPath = tempDir.resolve("junction").absolutePathString()
    IoTestUtil.createJunction(file.parent.absolutePathString(), junctionPath)

    val target = resolveSymLink(Path.of(junctionPath).resolve(file.fileName))
    assertEquals(file.absolutePathString(), target)
  }

  @Test
  fun hiddenDir() {
    assumeWindows()
    val dir = tempDir.newDirectory("dir")
    var attributes = getAttributes(dir)
    assertFalse(attributes.isHidden)
    Files.getFileAttributeView(dir, DosFileAttributeView::class.java).setHidden(true)
    attributes = getAttributes(dir)
    assertTrue(attributes.isHidden)
  }

  @Test
  fun hiddenFile() {
    assumeWindows()
    val file = tempDir.newFile("file")
    var attributes = getAttributes(file)
    assertFalse(attributes.isHidden)
    Files.getFileAttributeView(file, DosFileAttributeView::class.java).setHidden(true)
    attributes = getAttributes(file)
    assertTrue(attributes.isHidden)
  }

  @Test
  fun notSoHiddenRoot() {
    val absRoot = if (isWindows) {
      // Get SystemDrive from Eel environment
      val envVars = runBlocking { eelHolder.eel.exec.environmentVariables().eelIt().await() }
      val systemDrive = envVars["SystemDrive"] ?: "C:"
      EelPath.parse(systemDrive + '\\', eelHolder.eel.descriptor).asNioPath()
    }
    else {
      EelPath.parse("/", eelHolder.eel.descriptor).asNioPath()
    }
    val absAttributes = getAttributes(absRoot)
    assertFalse(absAttributes.isHidden)
  }

  @Test
  fun wellHiddenFile() {
    assumeWindows()
    val path = EelPath.parse("C:\\Documents and Settings\\desktop.ini", eelHolder.eel.descriptor).asNioPath()
    Assumptions.assumeTrue(Files.exists(path), "$path is not there")

    val attributes = getAttributes(path)
    assertEquals(FileAttributes.Type.FILE, attributes.getType())
    assertFalse(attributes.isSymLink)
    assertTrue(attributes.isHidden)
    assertTrue(attributes.isWritable)
    assertEquals(Files.size(path), attributes.length)
    assertEquals(Files.getLastModifiedTime(path).toMillis(), attributes.lastModified)
  }

  @Test
  fun extraLongName() {
    val prefix = StringUtil.repeatSymbol('a', 128) + "."
    var path = tempDir.newFile("$prefix.dir/$prefix.dir/$prefix.dir/$prefix.dir/$prefix.dir/$prefix.txt")
    Files.write(path, myTestData)

    assertFileAttributes(path)

    var target = resolveSymLink(path)
    assertEquals(path.absolutePathString(), target)

    if (isWindows) {
      val pathBuilder = StringBuilder(tempDir.absolutePathString())
      val length = 250 - pathBuilder.length
      pathBuilder.append("\\x_x_x_x_x".repeat(max(0, length / 10)))

      val baseDir = Path.of(pathBuilder.toString())
      Files.createDirectories(baseDir)
      assertTrue(getAttributes(baseDir).isDirectory)

      for (i in 1..100) {
        val dir = baseDir.resolve(StringUtil.repeat("x", i))
        Files.createDirectory(dir)
        assertTrue(getAttributes(dir).isDirectory)

        path = dir.resolve("file.txt")
        Files.write(path, myTestData)
        assertTrue(Files.exists(path))
        assertFileAttributes(path)

        target = resolveSymLink(path)
        assertEquals(path.absolutePathString(), target)
      }
    }
  }

  @Test
  fun subst() {
    assumeWindows()
    Assumptions.assumeTrue(isLocal)

    tempDir.newFile("file.txt") // just to populate a directory
    IoTestUtil.performTestOnWindowsSubst(tempDir.absolutePathString(), Consumer { substRoot: File ->
      val attributes = getAttributes(substRoot.toPath())
      assertEquals(FileAttributes.Type.DIRECTORY, attributes.getType(), "$substRoot $attributes")
      assertFalse(attributes.isSymLink, "$substRoot $attributes")

      val children = substRoot.listFiles()
      assertNotNull(children)
      assertEquals(1, children.size.toLong(), "only one child expected, found ${children.toList()}")
      val filePath = children[0].toPath()
      val target = resolveSymLink(filePath)
      assertEquals(filePath.absolutePathString(), target)
    })
  }

  @Test
  fun hardLink() {
    assumeHardLinkCreationIsSupported()
    val target = tempDir.newFile("file.txt")
    val link = tempDir.resolve("link")
    Files.createLink(link, target)

    var attributes = getAttributes(link)
    assertEquals(FileAttributes.Type.FILE, attributes.getType())
    assertEquals(Files.size(target), attributes.length)
    assertEquals(Files.getLastModifiedTime(target).toMillis(), attributes.lastModified)

    Files.write(target, myTestData)
    val newLastModified = attributes.lastModified - 5000
    Files.setLastModifiedTime(target, FileTime.fromMillis(newLastModified))
    assertTrue(Files.size(target) > 0)
    assertEquals(newLastModified, Files.getLastModifiedTime(target).toMillis())

    if (isWindows) {
      val bytes = Files.readAllBytes(link)
      assertEquals(myTestData.size.toLong(), bytes.size.toLong())
    }

    attributes = getAttributes(link)
    assertEquals(FileAttributes.Type.FILE, attributes.getType())
    assertEquals(Files.size(target), attributes.length)
    assertEquals(Files.getLastModifiedTime(target).toMillis(), attributes.lastModified)

    val resolved = resolveSymLink(link)
    assertEquals(link.absolutePathString(), resolved)
  }

  @Test
  fun stamps() {
    var attributes = getAttributes(tempDir)
    Assumptions.assumeTrue(
      attributes.lastModified > attributes.lastModified / 1000 * 1000,
      "expected FS has millisecond resolution but got lastModified: " + attributes.lastModified
    )

    var t1 = System.currentTimeMillis()
    TimeoutUtil.sleep(10)
    val path = tempDir.newFile("test.txt")
    TimeoutUtil.sleep(10)
    var t2 = System.currentTimeMillis()
    attributes = getAttributes(path)
    assertThat(attributes.lastModified).isBetween(t1, t2)

    t1 = System.currentTimeMillis()
    TimeoutUtil.sleep(10)
    Files.write(path, myTestData)
    TimeoutUtil.sleep(10)
    t2 = System.currentTimeMillis()
    attributes = getAttributes(path)
    assertThat(attributes.lastModified).isBetween(t1, t2)

    val exitCode = runBlocking {
      val process = if (isWindows) {
        eelHolder.eel.exec.spawnProcess("attrib").args("-A", path.asEelPath().toString()).eelIt()
      }
      else {
        eelHolder.eel.exec.spawnProcess("chmod").args("644", path.asEelPath().toString()).eelIt()
      }
      process.exitCode.await()
    }
    assertEquals(0, exitCode)
    attributes = getAttributes(path)
    assertThat(attributes.lastModified).isBetween(t1, t2)
  }

  @Test
  fun notOwned() {
    // Get user home directory from Eel environment
    val envVars = runBlocking { eelHolder.eel.exec.environmentVariables().eelIt().await() }
    val userHomeStr = if (isWindows) {
      envVars["USERPROFILE"] ?: return
    }
    else {
      envVars["HOME"] ?: return
    }
    val userHome = EelPath.parse(userHomeStr, eelHolder.eel.descriptor).asNioPath()

    val homeAttributes = getAttributes(userHome)
    assertTrue(homeAttributes.isDirectory)
    assertTrue(homeAttributes.isWritable)

    val parentAttributes = getAttributes(userHome.parent)
    assertTrue(parentAttributes.isDirectory)

    if (isUnix) {
      if (isLocal) {
        // optimized treating every directory as writable
        assertTrue(parentAttributes.isWritable)
      }
      else {
        assertFalse(parentAttributes.isWritable)
      }
      val mutantPath = tempDir.newFile("mutant")
      Files.setPosixFilePermissions(mutantPath, PosixFilePermissions.fromString("r--rw-rw-"))
      val mutantAttrs = getAttributes(mutantPath)
      assertTrue(mutantAttrs.isFile)
      assertFalse(mutantAttrs.isWritable)

      val devNull = getAttributes(EelPath.parse("/dev/null", eelHolder.eel.descriptor).asNioPath())
      assertTrue(devNull.isSpecial)
      assertTrue(devNull.isWritable)

      val etcPasswd = getAttributes(EelPath.parse("/etc/passwd", eelHolder.eel.descriptor).asNioPath())
      assertTrue(etcPasswd.isFile)
      assertFalse(etcPasswd.isWritable)
    }
  }

  @Test
  fun unicodeName() {
    val name = IoTestUtil.getUnicodeName()
    Assumptions.assumeTrue(name != null, "Unicode names not supported")
    val path = tempDir.newFile("$name.txt")
    Files.write(path, myTestData)

    assertFileAttributes(path)

    val target = resolveSymLink(path)
    assertEquals(path.absolutePathString(), target)
  }

  // Helper properties and methods to check Eel OS
  private val isWindows: Boolean
    get() = eelHolder.eel.descriptor.osFamily == EelOsFamily.Windows

  private val isUnix: Boolean
    get() = eelHolder.eel.descriptor.osFamily == EelOsFamily.Posix

  private val isLocal: Boolean
    get() = eelHolder.eel.descriptor == LocalEelDescriptor

  private fun assumeWindows() {
    Assumptions.assumeTrue(isWindows)
  }

  private fun assumeUnix() {
    Assumptions.assumeTrue(isUnix)
  }

  private fun assumeSymLinkCreationIsSupported() {
    if (isLocal) {
      IoTestUtil.assumeSymLinkCreationIsSupported()
    }
  }

  private fun assumeHardLinkCreationIsSupported() {
    Assumptions.assumeTrue(isLocal)
  }

  companion object {

    private fun resolveSymLink(path: Path): String? {
      val realPath = FileSystemUtil.resolveSymLink(path.absolutePathString())
      if (realPath != null && (path.getEelDescriptor().osFamily == EelOsFamily.Windows && realPath.startsWith("\\\\") || Files.exists(Path.of(realPath)))) {
        return realPath
      }
      return null
    }

    private fun Path.newFile(name: String): Path {
      val path = resolve(name)
      path.parent?.let { Files.createDirectories(it) }
      Files.createFile(path)
      return path
    }

    private fun Path.newDirectory(name: String): Path {
      val path = resolve(name)
      Files.createDirectories(path)
      return path
    }

    private fun getAttributes(path: Path): FileAttributes {
      val attributes = readAttributesUsingEel(path)
      assertNotNull(attributes, path.toString() + ", exists=" + Files.exists(path))
      return attributes
    }

    private fun getAttributesNullable(path: Path): FileAttributes? {
      val directoryListElement = listWithAttributesUsingEel(path, setOf(path.name))[path.name]
      val singleFileAttributes = try {
        readAttributesUsingEel(path)
      } catch (e: IOException) {
        null
      }
      assertEquals(directoryListElement, singleFileAttributes)
      return singleFileAttributes
    }

    private fun assertFileAttributes(path: Path) {
      val attributes = getAttributes(path)
      assertEquals(FileAttributes.Type.FILE, attributes.getType())
      assertFalse(attributes.isSymLink)
      assertFalse(attributes.isHidden)
      assertTrue(attributes.isWritable)
      assertEquals(Files.size(path), attributes.length)
      assertEquals(Files.getLastModifiedTime(path).toMillis(), attributes.lastModified)
      assertTrue(attributes.isWritable)
    }
  }
}
