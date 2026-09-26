// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.local

import com.intellij.concurrency.JobScheduler
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.IoTestUtil.assumeSymLinkCreationIsSupported
import com.intellij.openapi.util.io.IoTestUtil.assumeWindows
import com.intellij.openapi.util.io.IoTestUtil.createJunction
import com.intellij.openapi.util.io.IoTestUtil.deleteJunction
import com.intellij.openapi.util.io.IoTestUtil.getUnicodeName
import com.intellij.openapi.util.io.IoTestUtil.performTestOnWindowsSubst
import com.intellij.openapi.util.io.NioFiles
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
import com.intellij.openapi.vfs.local.FileWatcherTestUtil.wait
import com.intellij.openapi.vfs.newvfs.NewVirtualFile
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.testFramework.RunAll
import com.intellij.testFramework.TestLoggerFactory
import com.intellij.testFramework.VfsTestUtil
import com.intellij.testFramework.fixtures.BareTestFixtureTestCase
import com.intellij.testFramework.rules.TempDirectory
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.TimeoutUtil
import com.intellij.util.concurrency.Semaphore
import com.intellij.util.system.LowLevelLocalMachineAccess
import com.intellij.util.system.OS
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Locale
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.copyToRecursively
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.deleteExisting
import kotlin.io.path.deleteRecursively
import kotlin.io.path.name
import kotlin.io.path.pathString
import kotlin.io.path.writeText

@OptIn(ExperimentalPathApi::class, LowLevelLocalMachineAccess::class)
class FileWatcherTest : BareTestFixtureTestCase() {
  //<editor-fold desc="Set up / tear down">
  private val LOG = logger<FileWatcherTest>()

  @Rule @JvmField val tempDir = TempDirectory()

  private lateinit var fs: LocalFileSystem
  private lateinit var vfsTempDir: VirtualFile
  private lateinit var watcher: FileWatcher

  private val watchedPaths = mutableListOf<String>()
  private val scheduledJob = AtomicReference<ScheduledFuture<*>>()
  private val watcherEvents = Semaphore()
  private val resetHappened = AtomicBoolean()

  @Before fun setUp() {
    TestLoggerFactory.enableTraceLogging(testRootDisposable, NativeFileWatcherImpl::class.java, FileWatcherTest::class.java)
    LOG.debug("================== setting up " + getTestName(false) + " ==================")

    fs = LocalFileSystem.getInstance()
    vfsTempDir = refresh(tempDir.rootPath)

    runInEdtAndWait { fs.refresh(false) }
    runInEdtAndWait { fs.refresh(false) }

    watcher = (fs as LocalFileSystemImpl).fileWatcher
    assertFalse(watcher.isOperational)
    watchedPaths += tempDir.rootPath.toString()
    startup(watcher) { path ->
      if (path == FileWatcher.RESET || path != FileWatcher.OTHER && watchedPaths.any { path.startsWith(it) }) {
        scheduledJob.getAndSet(JobScheduler.getScheduler().schedule(watcherEvents::up, INTER_RESPONSE_DELAY, TimeUnit.MILLISECONDS))?.cancel(false)
        if (path == FileWatcher.RESET) resetHappened.set(true)
      }
    }

    LOG.debug("================== setting up " + getTestName(false) + " ==================")
  }

  @After fun tearDown() {
    LOG.debug("================== tearing down " + getTestName(false) + " ==================")

    RunAll(
      { scheduledJob.getAndSet(null)?.cancel(false) },
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
    val files = arrayOf(tempDir.newFileNio("test1.txt"), tempDir.newFileNio("test2.txt"))
    files.forEach { refresh(it) }
    files.forEach { watch(it, recursive = false) }

    assertEvents({ files.forEach { it.writeText("new content") } }, files.associateWith { 'U' })
    assertEvents({ files.forEach { it.deleteExisting() } }, files.associateWith { 'D' })
    assertEvents({ files.forEach { it.writeText("re-creation") } }, files.associateWith { 'C' })
  }

  @Test fun testFileRootRecursive() {
    val files = arrayOf(tempDir.newFileNio("test1.txt"), tempDir.newFileNio("test2.txt"))
    files.forEach { refresh(it) }
    files.forEach { watch(it, recursive = true) }

    assertEvents({ files.forEach { it.writeText("new content") } }, files.associateWith { 'U' })
    assertEvents({ files.forEach { it.deleteExisting() } }, files.associateWith { 'D' })
    assertEvents({ files.forEach { it.writeText("re-creation") } }, files.associateWith { 'C' })
  }

  @Test fun testNonCanonicallyNamedFileRoot() {
    assumeTrue("case-insensitive FS only", !SystemInfo.isFileSystemCaseSensitive)

    val file = tempDir.newFileNio("test.txt")
    refresh(file)

    watch(Path.of(file.toString().uppercase(Locale.US)))
    assertEvents({ file.writeText("new content") }, mapOf(file to 'U'))
    assertEvents({ file.deleteExisting() }, mapOf(file to 'D'))
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
    assertEvents({ file.deleteExisting() }, mapOf(file to 'D'))
    assertEvents({ file.writeText("re-creation") }, mapOf(file to 'C'))
  }

  @Test fun testDirectoryFlat() {
    val top = tempDir.newDirectoryPath("top")
    val watchedFile = tempDir.newFileNio("top/test.txt")
    val unwatchedFile = tempDir.newFileNio("top/sub/test.txt")
    refresh(top)

    watch(top, recursive = false)
    assertEvents({ watchedFile.writeText("new content") }, mapOf(watchedFile to 'U'))
    assertEvents({ unwatchedFile.writeText("new content") }, mapOf(), SHORT_PROCESS_DELAY)
  }

  @Test fun testDirectoryMixed() {
    val top = tempDir.newDirectoryPath("top")
    val sub = tempDir.newDirectoryPath("top/sub2")
    val unwatchedFile = tempDir.newFileNio("top/sub1/test.txt")
    val watchedFile1 = tempDir.newFileNio("top/test.txt")
    val watchedFile2 = tempDir.newFileNio("top/sub2/sub/test.txt")
    refresh(top)

    watch(top, recursive = false)
    watch(sub, recursive = true)
    assertEvents(
      { arrayOf(watchedFile1, watchedFile2, unwatchedFile).forEach { it.writeText("new content") } },
      mapOf(watchedFile1 to 'U', watchedFile2 to 'U')
    )
  }

  @Test fun testDirectoryScopeSwitching() {
    val top = tempDir.newDirectoryPath("top")
    val topFile = tempDir.newFileNio("top/test.txt")
    val subFile = tempDir.newFileNio("top/sub/test.txt")
    val files = arrayOf(topFile, subFile)
    refresh(top)

    val flatRequest = watch(top, recursive = false)
    assertEvents({ files.forEach { it.writeText(".") } }, mapOf(topFile to 'U'))

    val recursiveRequest = watch(top, recursive = true)
    assertEvents({ files.forEach { it.writeText("..") } }, mapOf(topFile to 'U', subFile to 'U'))

    unwatch(recursiveRequest)
    assertEvents({ files.forEach { it.writeText("...") } }, mapOf(topFile to 'U'))

    watch(top, recursive = true)
    assertEvents({ files.forEach { it.writeText("....") } }, mapOf(topFile to 'U', subFile to 'U'))

    unwatch(flatRequest)
    assertEvents({ files.forEach { it.writeText(".....") } }, mapOf(topFile to 'U', subFile to 'U'))
  }

  @Test fun testMove() {
    val top = tempDir.newDirectoryPath("top")
    val srcFile = tempDir.newFileNio("top/src/f")
    val srcDir = tempDir.newDirectoryPath("top/src/sub")
    tempDir.newFileNio("top/src/sub/f1")
    tempDir.newFileNio("top/src/sub/f2")
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
    val file = tempDir.newFileNio("root/file.zip")
    val pseudoDir = file.resolveSibling("sub/zip")
    refresh(root)

    watch(pseudoDir, recursive = false)
    assertEvents({ file.writeText("new content") }, mapOf(), SHORT_PROCESS_DELAY)
  }

  @Test fun testDirectoryOverlapping() {
    val top = tempDir.newDirectoryPath("top")
    val topFile = tempDir.newFileNio("top/file1.txt")
    val sub = tempDir.newDirectoryPath("top/sub")
    val subFile = tempDir.newFileNio("top/sub/file2.txt")
    val side = tempDir.newDirectoryPath("side")
    val sideFile = tempDir.newFileNio("side/file3.txt")
    refresh(top)
    refresh(side)

    watch(sub)
    watch(side)
    assertEvents(
      { arrayOf(subFile, sideFile).forEach { it.writeText("first content") } },
      mapOf(subFile to 'U', sideFile to 'U')
    )

    assertEvents(
      { arrayOf(topFile, subFile, sideFile).forEach { it.writeText("new content") } },
      mapOf(subFile to 'U', sideFile to 'U')
    )

    val requestForTopDir = watch(top)
    assertEvents(
      { arrayOf(topFile, subFile, sideFile).forEach { it.writeText("newer content") } },
      mapOf(topFile to 'U', subFile to 'U', sideFile to 'U')
    )
    unwatch(requestForTopDir)

    assertEvents(
      { arrayOf(topFile, subFile, sideFile).forEach { it.writeText("newest content") } },
      mapOf(subFile to 'U', sideFile to 'U')
    )

    assertEvents(
      { arrayOf(topFile, subFile, sideFile).forEach { it.deleteExisting() } },
      mapOf(topFile to 'D', subFile to 'D', sideFile to 'D')
    )
  }

  // ensure that flat roots set via symbolic paths behave correctly and do not report dirty files returned from other recursive roots
  @Test fun testSymbolicLinkIntoFlatRoot() {
    assumeSymLinkCreationIsSupported()

    val root = tempDir.newDirectoryPath("root")
    val cDir = tempDir.newDirectoryPath("root/A/B/C")
    val aLink = Files.createSymbolicLink(root.resolve("aLink"), root.resolve("A"))
    val flatWatchedFile = tempDir.newFileNio("root/aLink/test.txt")
    val fileOutsideFlatWatchRoot = tempDir.newFileNio("root/A/B/C/test.txt")
    refresh(root)

    watch(aLink, recursive = false)
    watch(cDir, recursive = false)
    assertEvents({ flatWatchedFile.writeText("new content") }, mapOf(flatWatchedFile to 'U'))
    assertEvents({ fileOutsideFlatWatchRoot.writeText("new content") }, mapOf(fileOutsideFlatWatchRoot to 'U'))
  }

  @Test fun testMultipleSymbolicLinkPathsToFile() {
    assumeSymLinkCreationIsSupported()

    val root = tempDir.newDirectoryPath("root")
    val file = tempDir.newFileNio("root/A/B/C/test.txt")
    val bLink = Files.createSymbolicLink(root.resolve("b-link"), root.resolve("A/B"))
    val cLink = Files.createSymbolicLink(root.resolve("c-link"), root.resolve("A/B/C"))
    refresh(root)
    val bFilePath = bLink.resolve("C/${file.name}")
    val cFilePath = cLink.resolve(file.name)

    watch(bLink)
    watch(cLink)
    assertEvents({ file.writeText("new content") }, mapOf(bFilePath to 'U', cFilePath to 'U'))
    assertEvents({ file.deleteExisting() }, mapOf(bFilePath to 'D', cFilePath to 'D'))
    assertEvents({ file.writeText("re-creation") }, mapOf(bFilePath to 'C', cFilePath to 'C'))
  }

  @Test fun testSymbolicLinkWatchRoot() {
    assumeSymLinkCreationIsSupported()

    val top = tempDir.newDirectoryPath("top")
    val file = tempDir.newFileNio("top/dir1/dir2/dir3/test.txt")
    val link = Files.createSymbolicLink(top.resolve("link"), top.resolve("dir1/dir2"))
    val fileLink = top.resolve("link/dir3/test.txt")
    refresh(top)

    watch(link)
    assertEvents({ file.writeText("new content") }, mapOf(fileLink to 'U'))
    assertEvents({ file.deleteExisting() }, mapOf(fileLink to 'D'))
    assertEvents({ file.writeText("re-creation") }, mapOf(fileLink to 'C'))
  }

  @Test fun testSymbolicLinkAboveWatchRoot() {
    assumeSymLinkCreationIsSupported()

    val top = tempDir.newDirectoryPath("top")
    val file = tempDir.newFileNio("top/dir1/dir2/dir3/test.txt")
    val link = Files.createSymbolicLink(top.resolve("link"), top.resolve("dir1/dir2"))
    val watchRoot = link.resolve("dir3")
    val fileLink = watchRoot.resolve(file.fileName)
    refresh(top)

    watch(watchRoot)
    assertEvents({ file.writeText("new content") }, mapOf(fileLink to 'U'))
    assertEvents({ file.deleteExisting() }, mapOf(fileLink to 'D'))
    assertEvents({ file.writeText("re-creation") }, mapOf(fileLink to 'C'))
  }

  @Test fun testJunctionWatchRoot() {
    assumeWindows()

    val top = tempDir.newDirectoryPath("top")
    val file = tempDir.newFileNio("top/dir1/dir2/dir3/test.txt")
    val junctionPath = "${top}/link"
    val junction = createJunction("${top}/dir1/dir2", junctionPath).toPath()
    try {
      val fileLink = top.resolve("link/dir3/test.txt")
      refresh(top)

      watch(junction)
      assertEvents({ file.writeText("new content") }, mapOf(fileLink to 'U'))
      assertEvents({ file.deleteExisting() }, mapOf(fileLink to 'D'))
      assertEvents({ file.writeText("re-creation") }, mapOf(fileLink to 'C'))
    }
    finally {
      deleteJunction(junctionPath)
    }
  }

  @Test fun testJunctionAboveWatchRoot() {
    assumeWindows()

    val top = tempDir.newDirectoryPath("top")
    val file = tempDir.newFileNio("top/dir1/dir2/dir3/test.txt")
    val junctionPath = "${top}/link"
    createJunction("${top}/dir1/dir2", junctionPath)
    try {
      val watchRoot = top.resolve("link/dir3")
      val fileLink = watchRoot.resolve(file.fileName)
      refresh(top)

      watch(watchRoot)

      assertEvents({ file.writeText("new content") }, mapOf(fileLink to 'U'))
      assertEvents({ file.deleteExisting() }, mapOf(fileLink to 'D'))
      assertEvents({ file.writeText("re-creation") }, mapOf(fileLink to 'C'))
    }
    finally {
      deleteJunction(junctionPath)
    }
  }

  @Test fun testSymlinkBelowWatchRoot() {
    assumeSymLinkCreationIsSupported()

    val top = tempDir.newDirectoryPath("top")
    val file = tempDir.newFileNio("top/dir1/dir2/dir3/test.txt")
    val link = Files.createSymbolicLink(top.resolve("link"), top.resolve("dir1/dir2"))
    val fileLink = link.resolve("dir3/${file.fileName}")
    refresh(top)
    watch(top)

    assertEvents({ file.writeText("new content") }, mapOf(fileLink to 'U', file to 'U'))
    assertEvents({ file.deleteExisting() }, mapOf(fileLink to 'D', file to 'D'))
    assertEvents({ file.writeText("re-creation") }, mapOf(fileLink to 'C', file to 'C'))
  }

  @Test fun testCircularSymlinkBelowWatchRoot() {
    assumeSymLinkCreationIsSupported()

    val top = tempDir.newDirectoryPath("top")
    val topA = tempDir.newDirectoryPath("top/a")
    val file = tempDir.newFileNio("top/dir1/dir2/dir3/test.txt")
    val link = Files.createSymbolicLink(topA.resolve("link"), top.resolve("dir1/dir2"))
    val link2 = Files.createSymbolicLink(file.resolveSibling("dir4"), topA)
    val fileLink = link.resolve("dir3/${file.fileName}")

    refresh(top)

    val request = watch(link.parent)
    watch(link2.parent)

    assertEvents({ file.writeText("new content") }, mapOf(fileLink to 'U', file to 'U'))
    assertEvents({ file.deleteExisting() }, mapOf(fileLink to 'D', file to 'D'))
    assertEvents({ file.writeText("re-creation") }, mapOf(fileLink to 'C', file to 'C'))

    unwatch(request)

    assertEvents({ file.writeText("new content") }, mapOf(file to 'U'))
  }

  @Test fun testSymlinkBelowWatchRootCreation() {
    assumeSymLinkCreationIsSupported()

    val top = tempDir.newDirectoryPath("top")
    val file = tempDir.newFileNio("top/dir1/dir2/dir3/test.txt")
    val link = top.resolve("link")
    val fileLink = link.resolve("dir3/${file.fileName}")
    refresh(top)
    watch(top)

    assertEvents({ file.writeText("new content") }, mapOf(file to 'U'))
    assertEvents({ Files.createSymbolicLink(link, top.resolve("dir1/dir2")) }, mapOf(link to 'C'))
    refresh(top)
    assertEvents({ file.writeText("newer content") }, mapOf(fileLink to 'U', file to 'U'))
    assertEvents({ link.deleteExisting() }, mapOf(link to 'D'))
    assertEvents({ file.writeText("even newer content") }, mapOf(file to 'U'))
  }

  @Test fun testJunctionBelowWatchRoot() {
    assumeWindows()

    val top = tempDir.newDirectoryPath("top")
    val file = tempDir.newFileNio("top/dir1/dir2/dir3/test.txt")
    val link = createJunction("${top}/dir1/dir2", "${top}/link").toPath()
    val fileLink = link.resolve("dir3/${file.fileName}")
    refresh(top)
    watch(top)

    assertEvents({ file.writeText("new content") }, mapOf(fileLink to 'U', file to 'U'))
    assertEvents({ file.deleteExisting() }, mapOf(fileLink to 'D', file to 'D'))
    assertEvents({ file.writeText("re-creation") }, mapOf(fileLink to 'C', file to 'C'))
  }

  @Test fun testJunctionBelowWatchRootCreation() {
    assumeWindows()

    val top = tempDir.newDirectoryPath("top")
    val file = tempDir.newFileNio("top/dir1/dir2/dir3/test.txt")
    val link = top.resolve("link")
    val fileLink = link.resolve("dir3/${file.fileName}")
    refresh(top)
    watch(top)

    assertEvents({ file.writeText("new content") }, mapOf(file to 'U'))
    assertEvents({ createJunction("${top}/dir1/dir2", link.toString()) }, mapOf(link to 'C'))
    refresh(top)
    assertEvents({ file.writeText("newer content") }, mapOf(fileLink to 'U', file to 'U'))
    assertEvents({ link.deleteExisting() }, mapOf(link to 'D'))
    assertEvents({ file.writeText("even newer content") }, mapOf(file to 'U'))
  }

  @Test fun testSubst() {
    assumeWindows()

    val target = tempDir.newDirectoryPath("top")
    val file = tempDir.newFileNio("top/sub/test.txt")

    performTestOnWindowsSubst(target.toString()) { substRoot ->
      val substRoot = substRoot.toPath()
      VfsRootAccess.allowRootAccess(testRootDisposable, substRoot.pathString)
      val vfsRoot = fs.findFileByNioFile(substRoot)!!
      watchedPaths += substRoot.pathString

      val substFile = substRoot.resolve("sub/test.txt")
      refresh(target)
      refresh(substRoot)

      try {
        watch(substRoot)
        assertEvents({ file.writeText("new content") }, mapOf(substFile to 'U'))

        val request = watch(target)
        assertEvents({ file.writeText("updated content") }, mapOf(file to 'U', substFile to 'U'))
        assertEvents({ file.deleteExisting() }, mapOf(file to 'D', substFile to 'D'))
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
    val file1 = tempDir.newFileNio("root/dir/file1.txt")
    val file2 = tempDir.newFileNio("root/dir/file2.txt")
    refresh(root)

    watch(root)
    assertEvents(
      {
        dir.deleteRecursively()
        dir.createDirectories()
        arrayOf(file1, file2).forEach { it.writeText("text") }
      },
      mapOf(file1 to 'U', file2 to 'U')
    )
  }

  @Test fun testWatchRootRecreation() {
    val root = tempDir.newDirectoryPath("root")
    val file1 = tempDir.newFileNio("root/file1.txt")
    val file2 = tempDir.newFileNio("root/file2.txt")
    refresh(root)

    watch(root)
    assertEvents(
      {
        root.deleteRecursively()
        root.createDirectories()
        if (OS.CURRENT == OS.Linux) TimeoutUtil.sleep(1500)  // implementation specific
        arrayOf (file1, file2).forEach { it.writeText("text") }
      },
      mapOf(file1 to 'U', file2 to 'U')
    )
    // ensuring the events still come after the root is restored
    assertEvents({ arrayOf (file1, file2).forEach { it.writeText("…") } }, mapOf(file1 to 'U', file2 to 'U'))
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
    assertEvents({ root.deleteRecursively() }, mapOf(root to 'D'))
    assertEvents({ root.createDirectories() }, mapOf(root to 'C'))
    assertEvents({ top.deleteRecursively() }, mapOf(top to 'D'))
    assertEvents({ root.createDirectories() }, mapOf(top to 'C'))
  }

  @Test fun testSwitchingToFsRoot() {
    val top = tempDir.newDirectoryPath("top")
    val root = tempDir.newDirectoryPath("top/root")
    val file1 = tempDir.newFileNio("top/1.txt")
    val file2 = tempDir.newFileNio("top/root/2.txt")
    refresh(top)
    val fsRoot = top.root
    assertTrue("can't guess root of ${top}", Files.isDirectory(fsRoot))

    val request = watch(root)
    assertEvents({ arrayOf(file1, file2).forEach { it.writeText("new content") } }, mapOf(file2 to 'U'))

    val rootRequest = watch(fsRoot, isManual = OS.CURRENT == OS.Linux)
    assertEvents({ arrayOf(file1, file2).forEach { it.writeText("12345") } }, mapOf(file1 to 'U', file2 to 'U'), SHORT_PROCESS_DELAY)
    unwatch(rootRequest)

    assertEvents({ arrayOf(file1, file2).forEach { it.writeText("") } }, mapOf(file2 to 'U'))

    unwatch(request)
    assertEvents({ arrayOf(file1, file2).forEach { it.writeText("xyz") } }, mapOf(), SHORT_PROCESS_DELAY)
  }

  @Test fun testLineBreaksInName() {
    assumeTrue("Unix-only", OS.isGenericUnix())

    val root = tempDir.newDirectoryPath("root")
    val file = tempDir.newFileNio("root/weird\ndir\nname/weird\nfile\nname")
    refresh(root)

    watch(root)
    assertEvents({ file.writeText("abc") }, mapOf(file to 'U'))
  }

  @Test fun testHiddenFiles() {
    assumeWindows()

    val root = tempDir.newDirectoryPath("root")
    val file = tempDir.newFileNio("root/dir/file")
    refresh(root)

    watch(root)
    assertEvents({ Files.setAttribute(file, "dos:hidden", true) }, mapOf(file to 'P'))
  }

  @Test fun testFileCaseChange() {
    assumeTrue("case-insensitive FS only", !SystemInfo.isFileSystemCaseSensitive)

    val root = tempDir.newDirectoryPath("root")
    val file = tempDir.newFileNio("root/file.txt")
    val newFile = file.resolveSibling("File.txt")
    refresh(root)

    watch(root)
    assertEvents({ Files.move(file, newFile, StandardCopyOption.ATOMIC_MOVE) }, mapOf(newFile to 'P'))
  }

  // the following tests verify the same scenarios with an active file watcher (prevents explicit marking of refreshed paths)
  @Test fun testPartialRefresh(): Unit = LocalFileSystemTest.doTestPartialRefresh(tempDir.newDirectoryPath("top"))
  @Test fun testInterruptedRefresh(): Unit = LocalFileSystemTest.doTestInterruptedRefresh(tempDir.newDirectoryPath("top"))
  @Test fun testRefreshAndFindFile(): Unit = LocalFileSystemTest.doTestRefreshAndFindFile(tempDir.newDirectoryPath("top"))
  @Test fun testRefreshEquality(): Unit = LocalFileSystemTest.doTestRefreshEquality(tempDir.newDirectoryPath("top"))

  @Test fun testUnicodePaths() {
    val name = getUnicodeName()
    assumeTrue("Unicode names not supported", name != null)

    val root = tempDir.newDirectoryPath(name!!)
    val file = tempDir.newFileNio("${name}/${name}.txt")
    refresh(root)
    watch(root)

    assertEvents({ file.writeText("abc") }, mapOf(file to 'U'))
  }

  @Test fun testDisplacementByIsomorphicTree() {
    assumeFalse("macOS-incompatible", OS.CURRENT == OS.macOS)

    val top = tempDir.newDirectoryPath("top")
    val root = tempDir.newDirectoryPath("top/root")
    val file = tempDir.newFileNio("top/root/middle/file.txt")
    file.writeText("original content")
    val root_copy = top.resolve("root_copy")
    root.copyToRecursively(root_copy, followLinks = false, overwrite = false)
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
    val file1 = tempDir.newFileNio("top/root1/file.txt")
    val file2 = tempDir.newFileNio("top/root2/file.txt")
    refresh(file1)
    refresh(file2)

    val request = watch(root1)
    assertEvents({ arrayOf(file1, file2).forEach { it.writeText("data") } }, mapOf(file1 to 'U'))
    fs.replaceWatchedRoot(request, root2.toString(), true)
    wait { watcher.isSettingRoots }
    assertEvents({ arrayOf(file1, file2).forEach { it.writeText("more data") } }, mapOf(file2 to 'U'))
  }

  @Test fun testPermissionUpdate() {
    val file = tempDir.newFileNio("test.txt")
    val vFile = refresh(file)
    assertTrue(vFile.isWritable)

    watch(file)
    assertEvents({ NioFiles.setReadOnly(file, true) }, mapOf(file to 'P'))
    assertFalse(vFile.isWritable)
    assertEvents({ NioFiles.setReadOnly(file, false) }, mapOf(file to 'P'))
    assertTrue(vFile.isWritable)
  }

  @Test fun testSyncRefreshNonWatchedFile() {
    val file = tempDir.newFileNio("test.txt")
    val vFile = refresh(file)
    file.writeText("new content")
    assertThat(VfsTestUtil.print(VfsTestUtil.getEvents { vFile.refresh(false, false) })).containsOnly("U : ${vFile.path}")
  }

  @Test fun testUncRoot() {
    assumeWindows()
    watch(Path.of("\\\\SRV\\share\\path"), isManual = true)
  }

  //<editor-fold desc="Helpers">
  private fun watch(root: Path, recursive: Boolean = true, isManual: Boolean = false): LocalFileSystem.WatchRequest {
    val request = fs.addRootToWatch(root.toString(), recursive)!!
    wait { watcher.isSettingRoots }
    assertThat(watcher.manualWatchRoots).let { if (isManual) it.contains(root.toString()) else it.doesNotContain(root.toString()) }
    return request
  }

  private fun unwatch(request: LocalFileSystem.WatchRequest) {
    fs.removeWatchedRoot(request)
    wait { watcher.isSettingRoots }
    fs.refresh(false)
  }

  private fun assertEvents(action: () -> Unit, expectedOps: Map<Path, Char>, timeout: Long = NATIVE_PROCESS_DELAY) {
    LOG.debug("** waiting for ${expectedOps}")
    TimeoutUtil.sleep(250)
    resetHappened.set(false)
    scheduledJob.getAndSet(null)?.cancel(false)
    watcherEvents.down()

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
