// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.local

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.IoTestUtil.*
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.impl.local.FileWatcher
import com.intellij.openapi.vfs.impl.local.LocalFileSystemImpl
import com.intellij.openapi.vfs.impl.local.NativeFileWatcherImpl
import com.intellij.openapi.vfs.local.FileWatcherTestUtil.INTER_RESPONSE_DELAY
import com.intellij.openapi.vfs.local.FileWatcherTestUtil.NATIVE_PROCESS_DELAY
import com.intellij.openapi.vfs.local.FileWatcherTestUtil.SHORT_PROCESS_DELAY
import com.intellij.openapi.vfs.local.FileWatcherTestUtil.refresh
import com.intellij.openapi.vfs.local.FileWatcherTestUtil.shutdown
import com.intellij.openapi.vfs.local.FileWatcherTestUtil.startup
import com.intellij.openapi.vfs.local.FileWatcherTestUtil.unwatch
import com.intellij.openapi.vfs.local.FileWatcherTestUtil.wait
import com.intellij.openapi.vfs.local.FileWatcherTestUtil.watch
import com.intellij.openapi.vfs.newvfs.NewVirtualFile
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.testFramework.*
import com.intellij.testFramework.fixtures.BareTestFixtureTestCase
import com.intellij.testFramework.rules.TempDirectory
import com.intellij.util.Alarm
import com.intellij.util.TimeoutUtil
import com.intellij.util.concurrency.Semaphore
import com.intellij.util.io.copyRecursively
import com.intellij.util.io.delete
import com.intellij.util.system.OS
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Assert.*
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.writeText

class FileWatcherTest : BareTestFixtureTestCase() {
  //<editor-fold desc="Set up / tear down">
  private val LOG = logger<FileWatcherTest>()

  @Rule @JvmField val tempDir = TempDirectory()

  private lateinit var fs: LocalFileSystem
  private lateinit var vfsTempDir: VirtualFile
  private lateinit var watcher: FileWatcher
  private lateinit var alarm: Alarm

  private val watchedPaths = mutableListOf<String>()
  private val watcherEvents = Semaphore()
  private val resetHappened = AtomicBoolean()

  @Before fun setUp() {
    TestLoggerFactory.enableTraceLogging(testRootDisposable, NativeFileWatcherImpl::class.java, FileWatcherTest::class.java)
    LOG.debug("================== setting up " + getTestName(false) + " ==================")

    fs = LocalFileSystem.getInstance()
    vfsTempDir = refresh(tempDir.rootPath)

    runInEdtAndWait { fs.refresh(false) }
    runInEdtAndWait { fs.refresh(false) }

    alarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, testRootDisposable)

    watcher = (fs as LocalFileSystemImpl).fileWatcher
    assertFalse(watcher.isOperational)
    watchedPaths += tempDir.rootPath.toString()
    startup(watcher) { path ->
      if (path === FileWatcher.RESET || path !== FileWatcher.OTHER && watchedPaths.any { path.startsWith(it) }) {
        alarm.cancelAllRequests()
        alarm.addRequest({ watcherEvents.up() }, INTER_RESPONSE_DELAY)
        if (path == FileWatcher.RESET) resetHappened.set(true)
      }
    }

    LOG.debug("================== setting up " + getTestName(false) + " ==================")
  }

  @After fun tearDown() {
    LOG.debug("================== tearing down " + getTestName(false) + " ==================")

    RunAll(
      { if (this::watcher.isInitialized) shutdown(watcher) },
      {
        runInEdtAndWait {
          if (this::vfsTempDir.isInitialized) runWriteAction { vfsTempDir.delete(this) }
          if (this::fs.isInitialized) (fs as LocalFileSystemImpl).cleanupForNextTest()
        }
      },
    ).run()

    LOG.debug("================== tearing down " + getTestName(false) + " ==================")
  }
  //</editor-fold>

  @Test fun testWatchRequestConvention() {
    val dir = tempDir.newDirectoryPath("dir")
    val r1 = watch(dir)
    val r2 = watch(dir)
    assertNotSame(r1, r2)
  }

  @Test fun testFileRoot() {
    val files = arrayOf(tempDir.newFile("test1.txt").toPath(), tempDir.newFile("test2.txt").toPath())
    files.forEach { refresh(it) }
    files.forEach { watch(it, false) }

    assertEvents({ files.forEach { it.writeText("new content") } }, files.associateWith { 'U' })
    assertEvents({ files.forEach { it.delete() } }, files.associateWith { 'D' })
    assertEvents({ files.forEach { it.writeText("re-creation") } }, files.associateWith { 'C' })
  }

  @Test fun testFileRootRecursive() {
    val files = arrayOf(tempDir.newFile("test1.txt").toPath(), tempDir.newFile("test2.txt").toPath())
    files.forEach { refresh(it) }
    files.forEach { watch(it, true) }

    assertEvents({ files.forEach { it.writeText("new content") } }, files.associateWith { 'U' })
    assertEvents({ files.forEach { it.delete() } }, files.associateWith { 'D' })
    assertEvents({ files.forEach { it.writeText("re-creation") } }, files.associateWith { 'C' })
  }

  @Test fun testNonCanonicallyNamedFileRoot() {
    assumeTrue("case-insensitive FS only", !SystemInfo.isFileSystemCaseSensitive)

    val file = tempDir.newFile("test.txt").toPath()
    refresh(file)

    watch(Path.of(file.toString().uppercase(Locale.US)))
    assertEvents({ file.writeText("new content") }, mapOf(file to 'U'))
    assertEvents({ file.delete() }, mapOf(file to 'D'))
    assertEvents({ file.writeText("re-creation") }, mapOf(file to 'C'))
  }

  @Test fun testDirectoryRecursive() {
    val top = tempDir.newDirectoryPath("top")
    val sub = top.resolve("sub")
    val file = sub.resolve("test.txt")
    refresh(top)

    watch(top)
    assertEvents({ sub.createDirectories() }, mapOf(sub to 'C'))
    refresh(sub)
    assertEvents({ file.createFile() }, mapOf(file to 'C'))
    assertEvents({ file.writeText("new content") }, mapOf(file to 'U'))
    assertEvents({ file.delete() }, mapOf(file to 'D'))
    assertEvents({ file.writeText("re-creation") }, mapOf(file to 'C'))
  }

  @Test fun testDirectoryFlat() {
    val top = tempDir.newDirectoryPath("top")
    val watchedFile = tempDir.newFile("top/test.txt").toPath()
    val unwatchedFile = tempDir.newFile("top/sub/test.txt").toPath()
    refresh(top)

    watch(top, false)
    assertEvents({ watchedFile.writeText("new content") }, mapOf(watchedFile to 'U'))
    assertEvents({ unwatchedFile.writeText("new content") }, mapOf(), SHORT_PROCESS_DELAY)
  }

  @Test fun testDirectoryMixed() {
    val top = tempDir.newDirectoryPath("top")
    val sub = tempDir.newDirectoryPath("top/sub2")
    val unwatchedFile = tempDir.newFile("top/sub1/test.txt").toPath()
    val watchedFile1 = tempDir.newFile("top/test.txt").toPath()
    val watchedFile2 = tempDir.newFile("top/sub2/sub/test.txt").toPath()
    refresh(top)

    watch(top, false)
    watch(sub, true)
    assertEvents(
      { arrayOf(watchedFile1, watchedFile2, unwatchedFile).forEach { it.writeText("new content") } },
      mapOf(watchedFile1 to 'U', watchedFile2 to 'U'))
  }

  @Test fun testMove() {
    val top = tempDir.newDirectoryPath("top")
    val srcFile = tempDir.newFile("top/src/f").toPath()
    val srcDir = tempDir.newDirectoryPath("top/src/sub")
    tempDir.newFile("top/src/sub/f1")
    tempDir.newFile("top/src/sub/f2")
    val dst = tempDir.newDirectoryPath("top/dst")
    val dstFile = dst.resolve(srcFile.fileName)
    val dstDir = dst.resolve(srcDir.fileName)
    refresh(top)

    watch(top)
    assertEvents({ Files.move(srcFile, dstFile, StandardCopyOption.ATOMIC_MOVE) }, mapOf(srcFile to 'D', dstFile to 'C'))
    assertEvents({ Files.move(srcDir, dstDir, StandardCopyOption.ATOMIC_MOVE) }, mapOf(srcDir to 'D', dstDir to 'C'))
  }

  @Test fun testIncorrectPath() {
    val root = tempDir.newDirectoryPath("root")
    val file = tempDir.newFile("root/file.zip").toPath()
    val pseudoDir = file.resolveSibling("sub/zip")
    refresh(root)

    watch(pseudoDir, false)
    assertEvents({ file.writeText("new content") }, mapOf(), SHORT_PROCESS_DELAY)
  }

  @Test fun testDirectoryOverlapping() {
    val top = tempDir.newDirectoryPath("top")
    val topFile = tempDir.newFile("top/file1.txt").toPath()
    val sub = tempDir.newDirectoryPath("top/sub")
    val subFile = tempDir.newFile("top/sub/file2.txt").toPath()
    val side = tempDir.newDirectoryPath("side")
    val sideFile = tempDir.newFile("side/file3.txt").toPath()
    refresh(top)
    refresh(side)

    watch(sub)
    watch(side)
    assertEvents(
      { arrayOf(subFile, sideFile).forEach { it.writeText("first content") } },
      mapOf(subFile to 'U', sideFile to 'U'))

    assertEvents(
      { arrayOf(topFile, subFile, sideFile).forEach { it.writeText("new content") } },
      mapOf(subFile to 'U', sideFile to 'U'))

    val requestForTopDir = watch(top)
    assertEvents(
      { arrayOf(topFile, subFile, sideFile).forEach { it.writeText("newer content") } },
      mapOf(topFile to 'U', subFile to 'U', sideFile to 'U'))
    unwatch(requestForTopDir)

    assertEvents(
      { arrayOf(topFile, subFile, sideFile).forEach { it.writeText("newest content") } },
      mapOf(subFile to 'U', sideFile to 'U'))

    assertEvents(
      { arrayOf(topFile, subFile, sideFile).forEach { it.delete() } },
      mapOf(topFile to 'D', subFile to 'D', sideFile to 'D'))
  }

  // ensure that flat roots set via symbolic paths behave correctly and do not report dirty files returned from other recursive roots
  @Test fun testSymbolicLinkIntoFlatRoot() {
    assumeSymLinkCreationIsSupported()

    val root = tempDir.newDirectoryPath("root")
    val cDir = tempDir.newDirectoryPath("root/A/B/C")
    val aLink = Files.createSymbolicLink(root.resolve("aLink"), root.resolve("A"))
    val flatWatchedFile = tempDir.newFile("root/aLink/test.txt").toPath()
    val fileOutsideFlatWatchRoot = tempDir.newFile("root/A/B/C/test.txt").toPath()
    refresh(root)

    watch(aLink, false)
    watch(cDir, false)
    assertEvents({ flatWatchedFile.writeText("new content") }, mapOf(flatWatchedFile to 'U'))
    assertEvents({ fileOutsideFlatWatchRoot.writeText("new content") }, mapOf(fileOutsideFlatWatchRoot to 'U'))
  }

  @Test fun testMultipleSymbolicLinkPathsToFile() {
    assumeSymLinkCreationIsSupported()

    val root = tempDir.newDirectoryPath("root")
    val file = tempDir.newFile("root/A/B/C/test.txt")
    val bLink = Files.createSymbolicLink(root.resolve("bLink"), root.resolve("A/B"))
    val cLink = Files.createSymbolicLink(root.resolve("cLink"), root.resolve("A/B/C"))
    refresh(root)
    val bFilePath = bLink.resolve("C/${file.name}")
    val cFilePath = cLink.resolve(file.name)

    watch(bLink)
    watch(cLink)
    assertEvents({ file.writeText("new content") }, mapOf(bFilePath to 'U', cFilePath to 'U'))
    assertEvents({ file.delete() }, mapOf(bFilePath to 'D', cFilePath to 'D'))
    assertEvents({ file.writeText("re-creation") }, mapOf(bFilePath to 'C', cFilePath to 'C'))
  }

  @Test fun testSymbolicLinkWatchRoot() {
    assumeSymLinkCreationIsSupported()

    val top = tempDir.newDirectoryPath("top")
    val file = tempDir.newFile("top/dir1/dir2/dir3/test.txt").toPath()
    val link = Files.createSymbolicLink(top.resolve("link"), top.resolve("dir1/dir2"))
    val fileLink = top.resolve("link/dir3/test.txt")
    refresh(top)

    watch(link)
    assertEvents({ file.writeText("new content") }, mapOf(fileLink to 'U'))
    assertEvents({ file.delete() }, mapOf(fileLink to 'D'))
    assertEvents({ file.writeText("re-creation") }, mapOf(fileLink to 'C'))
  }

  @Test fun testSymbolicLinkAboveWatchRoot() {
    assumeSymLinkCreationIsSupported()

    val top = tempDir.newDirectoryPath("top")
    val file = tempDir.newFile("top/dir1/dir2/dir3/test.txt").toPath()
    val link = Files.createSymbolicLink(top.resolve("link"), top.resolve("dir1/dir2"))
    val watchRoot = link.resolve("dir3")
    val fileLink = watchRoot.resolve(file.fileName)
    refresh(top)

    watch(watchRoot)
    assertEvents({ file.writeText("new content") }, mapOf(fileLink to 'U'))
    assertEvents({ file.delete() }, mapOf(fileLink to 'D'))
    assertEvents({ file.writeText("re-creation") }, mapOf(fileLink to 'C'))
  }

  @Test fun testJunctionWatchRoot() {
    assumeWindows()

    val top = tempDir.newDirectoryPath("top")
    val file = tempDir.newFile("top/dir1/dir2/dir3/test.txt").toPath()
    val junctionPath = "${top}/link"
    val junction = createJunction("${top}/dir1/dir2", junctionPath).toPath()
    try {
      val fileLink = top.resolve("link/dir3/test.txt")
      refresh(top)

      watch(junction)
      assertEvents({ file.writeText("new content") }, mapOf(fileLink to 'U'))
      assertEvents({ file.delete() }, mapOf(fileLink to 'D'))
      assertEvents({ file.writeText("re-creation") }, mapOf(fileLink to 'C'))
    }
    finally {
      deleteJunction(junctionPath)
    }
  }

  @Test fun testJunctionAboveWatchRoot() {
    assumeWindows()

    val top = tempDir.newDirectoryPath("top")
    val file = tempDir.newFile("top/dir1/dir2/dir3/test.txt").toPath()
    val junctionPath = "${top}/link"
    createJunction("${top}/dir1/dir2", junctionPath)
    try {
      val watchRoot = top.resolve("link/dir3")
      val fileLink = watchRoot.resolve(file.fileName)
      refresh(top)

      watch(watchRoot)

      assertEvents({ file.writeText("new content") }, mapOf(fileLink to 'U'))
      assertEvents({ file.delete() }, mapOf(fileLink to 'D'))
      assertEvents({ file.writeText("re-creation") }, mapOf(fileLink to 'C'))
    }
    finally {
      deleteJunction(junctionPath)
    }
  }

  @Test fun testSymlinkBelowWatchRoot() {
    assumeSymLinkCreationIsSupported()

    val top = tempDir.newDirectoryPath("top")
    val file = tempDir.newFile("top/dir1/dir2/dir3/test.txt").toPath()
    val link = Files.createSymbolicLink(top.resolve("link"), top.resolve("dir1/dir2"))
    val fileLink = link.resolve("dir3/${file.fileName}")
    refresh(top)
    watch(top)

    assertEvents({ file.writeText("new content") }, mapOf(fileLink to 'U', file to 'U'))
    assertEvents({ file.delete() }, mapOf(fileLink to 'D', file to 'D'))
    assertEvents({ file.writeText("re-creation") }, mapOf(fileLink to 'C', file to 'C'))
  }

  @Test fun testCircularSymlinkBelowWatchRoot() {
    assumeSymLinkCreationIsSupported()

    val top = tempDir.newDirectoryPath("top")
    val topA = tempDir.newDirectoryPath("top/a")
    val file = tempDir.newFile("top/dir1/dir2/dir3/test.txt").toPath()
    val link = Files.createSymbolicLink(topA.resolve("link"), top.resolve("dir1/dir2"))
    val link2 = Files.createSymbolicLink(file.resolveSibling("dir4"), topA)
    val fileLink = link.resolve("dir3/${file.fileName}")

    refresh(top)

    val request = watch(link.parent)
    watch(link2.parent)

    assertEvents({ file.writeText("new content") }, mapOf(fileLink to 'U', file to 'U'))
    assertEvents({ file.delete() }, mapOf(fileLink to 'D', file to 'D'))
    assertEvents({ file.writeText("re-creation") }, mapOf(fileLink to 'C', file to 'C'))

    unwatch(request)

    assertEvents({ file.writeText("new content") }, mapOf(file to 'U'))
  }

  @Test fun testSymlinkBelowWatchRootCreation() {
    assumeSymLinkCreationIsSupported()

    val top = tempDir.newDirectoryPath("top")
    val file = tempDir.newFile("top/dir1/dir2/dir3/test.txt").toPath()
    val link = top.resolve("link")
    val fileLink = link.resolve("dir3/${file.fileName}")
    refresh(top)
    watch(top)

    assertEvents({ file.writeText("new content") }, mapOf(file to 'U'))
    assertEvents({ Files.createSymbolicLink(link, top.resolve("dir1/dir2")) }, mapOf(link to 'C'))
    refresh(top)
    assertEvents({ file.writeText("newer content") }, mapOf(fileLink to 'U', file to 'U'))
    assertEvents({ link.delete() }, mapOf(link to 'D'))
    assertEvents({ file.writeText("even newer content") }, mapOf(file to 'U'))
  }

  @Test fun testJunctionBelowWatchRoot() {
    assumeWindows()

    val top = tempDir.newDirectoryPath("top")
    val file = tempDir.newFile("top/dir1/dir2/dir3/test.txt").toPath()
    val link = createJunction("${top}/dir1/dir2", "${top}/link").toPath()
    val fileLink = link.resolve("dir3/${file.fileName}")
    refresh(top)
    watch(top)

    assertEvents({ file.writeText("new content") }, mapOf(fileLink to 'U', file to 'U'))
    assertEvents({ file.delete() }, mapOf(fileLink to 'D', file to 'D'))
    assertEvents({ file.writeText("re-creation") }, mapOf(fileLink to 'C', file to 'C'))
  }

  @Test fun testJunctionBelowWatchRootCreation() {
    assumeWindows()

    val top = tempDir.newDirectoryPath("top")
    val file = tempDir.newFile("top/dir1/dir2/dir3/test.txt").toPath()
    val link = top.resolve("link")
    val fileLink = link.resolve("dir3/${file.fileName}")
    refresh(top)
    watch(top)

    assertEvents({ file.writeText("new content") }, mapOf(file to 'U'))
    assertEvents({ createJunction("${top}/dir1/dir2", link.toString()) }, mapOf(link to 'C'))
    refresh(top)
    assertEvents({ file.writeText("newer content") }, mapOf(fileLink to 'U', file to 'U'))
    assertEvents({ link.delete() }, mapOf(link to 'D'))
    assertEvents({ file.writeText("even newer content") }, mapOf(file to 'U'))
  }

  @Test fun testSubst() {
    assumeWindows()

    val target = tempDir.newDirectoryPath("top")
    val file = tempDir.newFile("top/sub/test.txt").toPath()

    performTestOnWindowsSubst(target.toString()) { substRoot ->
      VfsRootAccess.allowRootAccess(testRootDisposable, substRoot.path)
      val vfsRoot = fs.findFileByIoFile(substRoot)!!
      watchedPaths += substRoot.path

      val substFile = substRoot.toPath().resolve("sub/test.txt")
      refresh(target)
      refresh(substRoot.toPath())

      try {
        watch(substRoot.toPath())
        assertEvents({ file.writeText("new content") }, mapOf(substFile to 'U'))

        val request = watch(target)
        assertEvents({ file.writeText("updated content") }, mapOf(file to 'U', substFile to 'U'))
        assertEvents({ file.delete() }, mapOf(file to 'D', substFile to 'D'))
        unwatch(request)

        assertEvents({ file.writeText("re-creation") }, mapOf(substFile to 'C'))
      }
      finally {
        (vfsRoot as NewVirtualFile).markDirty()
        fs.refresh(false)
      }
    }
  }

  @Test fun testDirectoryRecreation() {
    val root = tempDir.newDirectoryPath("root")
    val dir = tempDir.newDirectoryPath("root/dir")
    val file1 = tempDir.newFile("root/dir/file1.txt").toPath()
    val file2 = tempDir.newFile("root/dir/file2.txt").toPath()
    refresh(root)

    watch(root)
    assertEvents(
      {
        dir.delete()
        dir.createDirectories()
        arrayOf(file1, file2).forEach { it.writeText("text") }
      },
      mapOf(file1 to 'U', file2 to 'U'))
  }

  @Test fun testWatchRootRecreation() {
    val root = tempDir.newDirectoryPath("root")
    val file1 = tempDir.newFile("root/file1.txt").toPath()
    val file2 = tempDir.newFile("root/file2.txt").toPath()
    refresh(root)

    watch(root)
    assertEvents(
      {
        root.delete()
        root.createDirectories()
        if (SystemInfo.isLinux) TimeoutUtil.sleep(1500)  // implementation specific
        arrayOf (file1, file2).forEach { it.writeText("text") }
      },
      mapOf(file1 to 'U', file2 to 'U'))
  }

  @Test fun testWatchNonExistingRoot() {
    val top = tempDir.rootPath.resolve("top")
    val root = tempDir.rootPath.resolve("top/d1/d2/d3/root")
    refresh(tempDir.rootPath)

    watch(root)
    assertEvents({ root.createDirectories() }, mapOf(top to 'C'))
  }

  @Test fun testWatchRootRenameRemove() {
    val top = tempDir.newDirectoryPath("top")
    val root = tempDir.newDirectoryPath("top/d1/d2/d3/root")
    val root2 = top.resolve("root2")
    refresh(top)

    watch(root)
    assertEvents({ Files.move(root, root2) }, mapOf(root to 'D', root2 to 'C'))
    assertEvents({ Files.move(root2, root) }, mapOf(root to 'C', root2 to 'D'))
    assertEvents({ root.delete() }, mapOf(root to 'D'))
    assertEvents({ root.createDirectories() }, mapOf(root to 'C'))
    assertEvents({ top.delete() }, mapOf(top to 'D'))
    assertEvents({ root.createDirectories() }, mapOf(top to 'C'))
  }

  @Test fun testSwitchingToFsRoot() {
    val top = tempDir.newDirectoryPath("top")
    val root = tempDir.newDirectoryPath("top/root")
    val file1 = tempDir.newFile("top/1.txt").toPath()
    val file2 = tempDir.newFile("top/root/2.txt").toPath()
    refresh(top)
    val fsRoot = top.root
    assertTrue("can't guess root of ${top}", Files.isDirectory(fsRoot))

    val request = watch(root)
    assertEvents({ arrayOf(file1, file2).forEach { it.writeText("new content") } }, mapOf(file2 to 'U'))

    val rootRequest = watch(fsRoot, isManual = SystemInfo.isLinux)
    assertEvents({ arrayOf(file1, file2).forEach { it.writeText("12345") } }, mapOf(file1 to 'U', file2 to 'U'), SHORT_PROCESS_DELAY)
    unwatch(rootRequest)

    assertEvents({ arrayOf(file1, file2).forEach { it.writeText("") } }, mapOf(file2 to 'U'))

    unwatch(request)
    assertEvents({ arrayOf(file1, file2).forEach { it.writeText("xyz") } }, mapOf(), SHORT_PROCESS_DELAY)
  }

  @Test fun testLineBreaksInName() {
    assumeTrue("Unix-only", OS.isGenericUnix())

    val root = tempDir.newDirectoryPath("root")
    val file = tempDir.newFile("root/weird\ndir\nname/weird\nfile\nname").toPath()
    refresh(root)

    watch(root)
    assertEvents({ file.writeText("abc") }, mapOf(file to 'U'))
  }

  @Test fun testHiddenFiles() {
    assumeWindows()

    val root = tempDir.newDirectoryPath("root")
    val file = tempDir.newFile("root/dir/file").toPath()
    refresh(root)

    watch(root)
    assertEvents({ Files.setAttribute(file, "dos:hidden", true) }, mapOf(file to 'P'))
  }

  @Test fun testFileCaseChange() {
    assumeTrue("case-insensitive FS only", !SystemInfo.isFileSystemCaseSensitive)

    val root = tempDir.newDirectoryPath("root")
    val file = tempDir.newFile("root/file.txt").toPath()
    val newFile = file.resolveSibling(StringUtil.capitalize(file.fileName.toString()))
    refresh(root)

    watch(root)
    assertEvents({ Files.move(file, newFile, StandardCopyOption.ATOMIC_MOVE) }, mapOf(newFile to 'P'))
  }

  // the following tests verify the same scenarios with an active file watcher (prevents explicit marking of refreshed paths)
  @Test fun testPartialRefresh(): Unit = LocalFileSystemTest.doTestPartialRefresh(tempDir.newDirectory("top"))
  @Test fun testInterruptedRefresh(): Unit = LocalFileSystemTest.doTestInterruptedRefresh(tempDir.newDirectory("top"))
  @Test fun testRefreshAndFindFile(): Unit = LocalFileSystemTest.doTestRefreshAndFindFile(tempDir.newDirectory("top"))
  @Test fun testRefreshEquality(): Unit = LocalFileSystemTest.doTestRefreshEquality(tempDir.newDirectory("top"))

  @Test fun testUnicodePaths() {
    val name = getUnicodeName()
    assumeTrue("Unicode names not supported", name != null)

    val root = tempDir.newDirectoryPath(name!!)
    val file = tempDir.newFile("${name}/${name}.txt").toPath()
    refresh(root)
    watch(root)

    assertEvents({ file.writeText("abc") }, mapOf(file to 'U'))
  }

  @Suppress("LocalVariableName")
  @Test fun testDisplacementByIsomorphicTree() {
    assumeFalse("macOS-incompatible", OS.CURRENT == OS.macOS)

    val top = tempDir.newDirectoryPath("top")
    val root = tempDir.newDirectoryPath("top/root")
    val file = tempDir.newFile("top/root/middle/file.txt").toPath()
    file.writeText("original content")
    val root_copy = top.resolve("root_copy")
    root.copyRecursively(root_copy)
    file.writeText("new content")
    val root_bak = top.resolve("root.bak")

    val vFile = fs.refreshAndFindFileByNioFile(file)!!
    assertThat(VfsUtilCore.loadText(vFile)).isEqualTo("new content")

    watch(root)
    assertEvents({ Files.move(root, root_bak); Files.move(root_copy, root) }, mapOf(file to 'U'))
    assertTrue(vFile.isValid)
    assertThat(VfsUtilCore.loadText(vFile)).isEqualTo("original content")
  }

  @Test fun testWatchRootReplacement() {
    val root1 = tempDir.newDirectoryPath("top/root1")
    val root2 = tempDir.newDirectoryPath("top/root2")
    val file1 = tempDir.newFile("top/root1/file.txt").toPath()
    val file2 = tempDir.newFile("top/root2/file.txt").toPath()
    refresh(file1)
    refresh(file2)

    val request = watch(root1)
    assertEvents({ arrayOf(file1, file2).forEach { it.writeText("data") } }, mapOf(file1 to 'U'))
    fs.replaceWatchedRoot(request, root2.toString(), true)
    wait { watcher.isSettingRoots }
    assertEvents({ arrayOf(file1, file2).forEach { it.writeText("more data") } }, mapOf(file2 to 'U'))
  }

  @Test fun testPermissionUpdate() {
    val file = tempDir.newFile("test.txt").toPath()
    val vFile = refresh(file)
    assertTrue(vFile.isWritable)
    val ro = if (SystemInfo.isWindows) arrayOf("attrib", "+R", file.toString()) else arrayOf("chmod", "500", file.toString())
    val rw = if (SystemInfo.isWindows) arrayOf("attrib", "-R", file.toString()) else arrayOf("chmod", "700", file.toString())

    watch(file)
    assertEvents({ PlatformTestUtil.assertSuccessful(GeneralCommandLine(*ro)) }, mapOf(file to 'P'))
    assertFalse(vFile.isWritable)
    assertEvents({ PlatformTestUtil.assertSuccessful(GeneralCommandLine(*rw)) }, mapOf(file to 'P'))
    assertTrue(vFile.isWritable)
  }

  @Test fun testSyncRefreshNonWatchedFile() {
    val file = tempDir.newFile("test.txt").toPath()
    val vFile = refresh(file)
    file.writeText("new content")
    assertThat(VfsTestUtil.print(VfsTestUtil.getEvents { vFile.refresh(false, false) })).containsOnly("U : ${vFile.path}")
  }

  @Test fun testUncRoot() {
    assumeWindows()
    watch(Path.of("\\\\SRV\\share\\path"), isManual = true)
  }

  //<editor-fold desc="Helpers">
  private fun watch(file: Path, recursive: Boolean = true, isManual: Boolean = false): LocalFileSystem.WatchRequest {
    val request = watch(watcher, file, recursive)
    assertThat(watcher.manualWatchRoots).let { if (isManual) it.contains(file.toString()) else it.doesNotContain(file.toString()) }
    return request
  }

  private fun unwatch(request: LocalFileSystem.WatchRequest) {
    unwatch(watcher, request)
    fs.refresh(false)
  }

  private fun assertEvents(action: () -> Unit, expectedOps: Map<Path, Char>, timeout: Long = NATIVE_PROCESS_DELAY) {
    LOG.debug("** waiting for ${expectedOps}")
    watcherEvents.down()
    alarm.cancelAllRequests()
    resetHappened.set(false)

    TimeoutUtil.sleep(250)

    action()
    LOG.debug("** action performed")

    watcherEvents.waitFor(timeout)
    watcherEvents.up()
    assumeFalse("reset happened", resetHappened.get())
    LOG.debug("** done waiting")

    val events = VfsTestUtil.getEvents { fs.refresh(false) }
      .filterNot { PathManager.getConfigDir().startsWith(it.path) || PathManager.getSystemDir().startsWith(it.path) }

    val expected = expectedOps.entries.map { "${it.value} : ${it.key.toString().replace('\\', '/')}" }.sorted()
    val actual = VfsTestUtil.print(events).sorted()
    assertEquals(expected, actual)
  }
  //</editor-fold>
}
