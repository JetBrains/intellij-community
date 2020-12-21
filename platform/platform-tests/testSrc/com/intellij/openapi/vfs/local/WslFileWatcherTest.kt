// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.local

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.IoTestUtil.*
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.impl.local.FileWatcher
import com.intellij.openapi.vfs.impl.local.LocalFileSystemImpl
import com.intellij.openapi.vfs.impl.wsl.WslFileWatcher
import com.intellij.openapi.vfs.local.FileWatcherTestUtil.INTER_RESPONSE_DELAY
import com.intellij.openapi.vfs.local.FileWatcherTestUtil.NATIVE_PROCESS_DELAY
import com.intellij.openapi.vfs.local.FileWatcherTestUtil.SHORT_PROCESS_DELAY
import com.intellij.openapi.vfs.local.FileWatcherTestUtil.refresh
import com.intellij.openapi.vfs.local.FileWatcherTestUtil.shutdown
import com.intellij.openapi.vfs.local.FileWatcherTestUtil.startup
import com.intellij.openapi.vfs.local.FileWatcherTestUtil.wait
import com.intellij.openapi.vfs.newvfs.NewVirtualFile
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.testFramework.*
import com.intellij.testFramework.fixtures.BareTestFixtureTestCase
import com.intellij.util.Alarm
import com.intellij.util.TimeoutUtil
import com.intellij.util.concurrency.Semaphore
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Assert.*
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.concurrent.atomic.AtomicBoolean

//todo[r.sh] fix ignored tests
class WslFileWatcherTest : BareTestFixtureTestCase() {
  //<editor-fold desc="Set up / tear down">
  companion object {
    private val LOG: Logger by lazy { Logger.getInstance(WslFileWatcher::class.java) }
    private val WSL: String? by lazy { enumerateWslDistributions().firstOrNull() }
  }

  private lateinit var tempDir: Path
  private lateinit var wsl: String
  private lateinit var fs: LocalFileSystem
  private lateinit var vfsTempDir: VirtualFile
  private lateinit var watcher: FileWatcher
  private lateinit var alarm: Alarm

  private val watchedPaths = mutableListOf<String>()
  private val watcherEvents = Semaphore()
  private val resetHappened = AtomicBoolean()

  @Before fun setUp() {
    assumeTrue(SystemInfo.isWin10OrNewer)
    assumeWslPresence()

    assumeTrue("No WSL distributions found", WSL != null)
    wsl = WSL!!
    assumeTrue("WSL distribution ${wsl} doesn't seem to be alive", reanimateWslDistribution(wsl))

    LOG.debug("================== setting up " + getTestName(false) + " ==================")

    tempDir = Files.createTempDirectory(Paths.get("\\\\wsl$\\${wsl}\\tmp"), "${UsefulTestCase.TEMP_DIR_MARKER}${getTestName(false)}_")
    fs = LocalFileSystem.getInstance()
    vfsTempDir = refresh(tempDir.toFile())

    runInEdtAndWait { fs.refresh(false) }
    runInEdtAndWait { fs.refresh(false) }

    alarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, testRootDisposable)

    watcher = (fs as LocalFileSystemImpl).fileWatcher
    assertFalse(watcher.isOperational)
    watchedPaths += tempDir.toString()
    startup(watcher) { path: String ->
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
      { if (this::tempDir.isInitialized) FileUtil.delete(tempDir) }
    ).run()

    LOG.debug("================== tearing down " + getTestName(false) + " ==================")
  }
  //</editor-fold>

  @Test fun testWatchRequestConvention() {
    val dir = tempDir.newDirectory("dir")
    val r1 = watch(dir)
    val r2 = watch(dir)
    assertNotSame(r1, r2)
  }

  @Test fun testFileRoot() {
    val files = arrayOf(tempDir.newFile("test1.txt"), tempDir.newFile("test2.txt"))
    files.forEach { refresh(it) }
    files.forEach { watch(it, false) }

    assertEvents({ files.forEach { it.writeText("new content") } }, files.map { it to 'U' }.toMap())
    assertEvents({ files.forEach { assertTrue(it.delete()) } }, files.map { it to 'D' }.toMap())
    assertEvents({ files.forEach { it.writeText("re-creation") } }, files.map { it to 'C' }.toMap())
  }

  @Test fun testFileRootRecursive() {
    val files = arrayOf(tempDir.newFile("test1.txt"), tempDir.newFile("test2.txt"))
    files.forEach { refresh(it) }
    files.forEach { watch(it, true) }

    assertEvents({ files.forEach { it.writeText("new content") } }, files.map { it to 'U' }.toMap())
    assertEvents({ files.forEach { assertTrue(it.delete()) } }, files.map { it to 'D' }.toMap())
    assertEvents({ files.forEach { it.writeText("re-creation") } }, files.map { it to 'C' }.toMap())
  }

  @Test fun testDirectoryRecursive() {
    val top = tempDir.newDirectory("top")
    val sub = File(top, "sub")
    val file = File(sub, "test.txt")
    refresh(top)

    watch(top)
    assertEvents({ sub.mkdir() }, mapOf(sub to 'C'))
    refresh(sub)
    assertEvents({ assertTrue(file.createNewFile()) }, mapOf(file to 'C'))
    assertEvents({ file.writeText("new content") }, mapOf(file to 'U'))
    assertEvents({ assertTrue(file.delete()) }, mapOf(file to 'D'))
    assertEvents({ file.writeText("re-creation") }, mapOf(file to 'C'))
  }

  @Test fun testDirectoryFlat() {
    val top = tempDir.newDirectory("top")
    val watchedFile = tempDir.newFile("top/test.txt")
    val unwatchedFile = tempDir.newFile("top/sub/test.txt")
    refresh(top)

    watch(top, false)
    assertEvents({ watchedFile.writeText("new content") }, mapOf(watchedFile to 'U'))
    assertEvents({ unwatchedFile.writeText("new content") }, mapOf(), SHORT_PROCESS_DELAY)
  }

  @Test fun testDirectoryMixed() {
    val top = tempDir.newDirectory("top")
    val sub = tempDir.newDirectory("top/sub2")
    val unwatchedFile = tempDir.newFile("top/sub1/test.txt")
    val watchedFile1 = tempDir.newFile("top/test.txt")
    val watchedFile2 = tempDir.newFile("top/sub2/sub/test.txt")
    refresh(top)

    watch(top, false)
    watch(sub, true)
    assertEvents(
      { arrayOf(watchedFile1, watchedFile2, unwatchedFile).forEach { it.writeText("new content") } },
      mapOf(watchedFile1 to 'U', watchedFile2 to 'U'))
  }

  @Test fun testMove() {
    val top = tempDir.newDirectory("top")
    val srcFile = tempDir.newFile("top/src/f")
    val srcDir = tempDir.newDirectory("top/src/sub")
    tempDir.newFile("top/src/sub/f1")
    tempDir.newFile("top/src/sub/f2")
    val dst = tempDir.newDirectory("top/dst")
    val dstFile = File(dst, srcFile.name)
    val dstDir = File(dst, srcDir.name)
    refresh(top)

    watch(top)
    assertEvents({ Files.move(srcFile.toPath(), dstFile.toPath(), StandardCopyOption.ATOMIC_MOVE) }, mapOf(srcFile to 'D', dstFile to 'C'))
    assertEvents({ Files.move(srcDir.toPath(), dstDir.toPath(), StandardCopyOption.ATOMIC_MOVE) }, mapOf(srcDir to 'D', dstDir to 'C'))
  }

  @Test fun testIncorrectPath() {
    val root = tempDir.newDirectory("root")
    val file = tempDir.newFile("root/file.zip")
    val pseudoDir = File(file.parent, "sub/zip")
    refresh(root)

    watch(pseudoDir, false)
    assertEvents({ file.writeText("new content") }, mapOf(), SHORT_PROCESS_DELAY)
  }

  @Test fun testDirectoryOverlapping() {
    val top = tempDir.newDirectory("top")
    val topFile = tempDir.newFile("top/file1.txt")
    val sub = tempDir.newDirectory("top/sub")
    val subFile = tempDir.newFile("top/sub/file2.txt")
    val side = tempDir.newDirectory("side")
    val sideFile = tempDir.newFile("side/file3.txt")
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
      { arrayOf(topFile, subFile, sideFile).forEach { assertTrue(it.delete()) } },
      mapOf(topFile to 'D', subFile to 'D', sideFile to 'D'))
  }

  // ensure that flat roots set via symbolic paths behave correctly and do not report dirty files returned from other recursive roots
  @Ignore("symlink resolution doesn't work over 9P")
  @Test fun testSymbolicLinkIntoFlatRoot() {
    assumeSymLinkCreationIsSupported()

    val root = tempDir.newDirectory("root")
    val cDir = tempDir.newDirectory("root/A/B/C")
    val aLink = File(root, "aLink")
    createSymbolicLink(aLink.toPath(), Paths.get("${root.path}/A"))
    val flatWatchedFile = tempDir.newFile("root/aLink/test.txt")
    val fileOutsideFlatWatchRoot = tempDir.newFile("root/A/B/C/test.txt")
    refresh(root)

    watch(aLink, false)
    watch(cDir, false)
    assertEvents({ flatWatchedFile.writeText("new content") }, mapOf(flatWatchedFile to 'U'))
    assertEvents({ fileOutsideFlatWatchRoot.writeText("new content") }, mapOf(fileOutsideFlatWatchRoot to 'U'))
  }

  @Ignore("symlink resolution doesn't work over 9P")
  @Test fun testMultipleSymbolicLinkPathsToFile() {
    assumeSymLinkCreationIsSupported()

    val root = tempDir.newDirectory("root")
    val file = tempDir.newFile("root/A/B/C/test.txt")
    val bLink = File(root, "bLink")
    createSymbolicLink(bLink.toPath(), Paths.get("${root.path}/A/B"))
    val cLink = File(root, "cLink")
    createSymbolicLink(cLink.toPath(), Paths.get("${root.path}/A/B/C"))
    refresh(root)
    val bFilePath = File(bLink.path, "C/${file.name}")
    val cFilePath = File(cLink.path, file.name)

    watch(bLink)
    watch(cLink)
    assertEvents({ file.writeText("new content") }, mapOf(bFilePath to 'U', cFilePath to 'U'))
    assertEvents({ assertTrue(file.delete()) }, mapOf(bFilePath to 'D', cFilePath to 'D'))
    assertEvents({ file.writeText("re-creation") }, mapOf(bFilePath to 'C', cFilePath to 'C'))
  }

  @Ignore("symlink resolution doesn't work over 9P")
  @Test fun testSymbolicLinkWatchRoot() {
    assumeSymLinkCreationIsSupported()

    val top = tempDir.newDirectory("top")
    val file = tempDir.newFile("top/dir1/dir2/dir3/test.txt")
    val link = createSymbolicLink(Paths.get(top.path, "link"), Paths.get("${top.path}/dir1/dir2")).toFile()
    val fileLink = File(top, "link/dir3/test.txt")
    refresh(top)

    watch(link)
    assertEvents({ file.writeText("new content") }, mapOf(fileLink to 'U'))
    assertEvents({ assertTrue(file.delete()) }, mapOf(fileLink to 'D'))
    assertEvents({ file.writeText("re-creation") }, mapOf(fileLink to 'C'))
  }

  @Ignore("symlink resolution doesn't work over 9P")
  @Test fun testSymbolicLinkAboveWatchRoot() {
    assumeSymLinkCreationIsSupported()

    val top = tempDir.newDirectory("top")
    val file = tempDir.newFile("top/dir1/dir2/dir3/test.txt")
    val link = createSymbolicLink(Paths.get(top.path, "link"), Paths.get("${top.path}/dir1/dir2")).toFile()
    val watchRoot = File(link, "dir3")
    val fileLink = File(watchRoot, file.name)
    refresh(top)

    watch(watchRoot)
    assertEvents({ file.writeText("new content") }, mapOf(fileLink to 'U'))
    assertEvents({ assertTrue(file.delete()) }, mapOf(fileLink to 'D'))
    assertEvents({ file.writeText("re-creation") }, mapOf(fileLink to 'C'))
  }

  @Ignore("symlink resolution doesn't work over 9P")
  @Test fun testJunctionWatchRoot() {
    val top = tempDir.newDirectory("top")
    val file = tempDir.newFile("top/dir1/dir2/dir3/test.txt")
    val junctionPath = "${top}/link"
    val junction = createJunction("${top.path}/dir1/dir2", junctionPath)
    try {
      val fileLink = File(top, "link/dir3/test.txt")
      refresh(top)

      watch(junction)
      assertEvents({ file.writeText("new content") }, mapOf(fileLink to 'U'))
      assertEvents({ assertTrue(file.delete()) }, mapOf(fileLink to 'D'))
      assertEvents({ file.writeText("re-creation") }, mapOf(fileLink to 'C'))
    }
    finally {
      deleteJunction(junctionPath)
    }
  }

  @Ignore("symlink resolution doesn't work over 9P")
  @Test fun testJunctionAboveWatchRoot() {
    val top = tempDir.newDirectory("top")
    val file = tempDir.newFile("top/dir1/dir2/dir3/test.txt")
    val junctionPath = "${top}/link"
    createJunction("${top.path}/dir1/dir2", junctionPath)
    try {
      val watchRoot = File(top, "link/dir3")
      val fileLink = File(watchRoot, file.name)
      refresh(top)

      watch(watchRoot)

      assertEvents({ file.writeText("new content") }, mapOf(fileLink to 'U'))
      assertEvents({ assertTrue(file.delete()) }, mapOf(fileLink to 'D'))
      assertEvents({ file.writeText("re-creation") }, mapOf(fileLink to 'C'))
    }
    finally {
      deleteJunction(junctionPath)
    }
  }

  @Ignore("symlink resolution doesn't work over 9P")
  @Test fun testSymlinkBelowWatchRoot() {
    assumeSymLinkCreationIsSupported()

    val top = tempDir.newDirectory("top")
    val file = tempDir.newFile("top/dir1/dir2/dir3/test.txt")
    val link = createSymbolicLink(Paths.get(top.path, "link"), Paths.get("${top.path}/dir1/dir2")).toFile()
    val fileLink = File(link, "dir3/" + file.name)
    refresh(top)
    watch(top)

    assertEvents({ file.writeText("new content") }, mapOf(fileLink to 'U', file to 'U'))
    assertEvents({ assertTrue(file.delete()) }, mapOf(fileLink to 'D', file to 'D'))
    assertEvents({ file.writeText("re-creation") }, mapOf(fileLink to 'C', file to 'C'))
  }

  @Ignore("symlink resolution doesn't work over 9P")
  @Test fun testCircularSymlinkBelowWatchRoot() {
    assumeSymLinkCreationIsSupported()

    val top = tempDir.newDirectory("top")
    val topA = tempDir.newDirectory("top/a")
    val file = tempDir.newFile("top/dir1/dir2/dir3/test.txt")
    val link = createSymbolicLink(Paths.get(topA.path, "link"), Paths.get("${top.path}/dir1/dir2")).toFile()
    val link2 = createSymbolicLink(Paths.get(file.parent, "dir4"), Paths.get(topA.path)).toFile()
    val fileLink = File(link, "dir3/" + file.name)

    refresh(top)

    val request = watch(link.parentFile)
    watch(link2.parentFile)

    assertEvents({ file.writeText("new content") }, mapOf(fileLink to 'U', file to 'U'))
    assertEvents({ assertTrue(file.delete()) }, mapOf(fileLink to 'D', file to 'D'))
    assertEvents({ file.writeText("re-creation") }, mapOf(fileLink to 'C', file to 'C'))

    unwatch(request)

    assertEvents({ file.writeText("new content") }, mapOf(file to 'U'))
  }

  @Ignore("symlink resolution doesn't work over 9P")
  @Test fun testSymlinkBelowWatchRootCreation() {
    assumeSymLinkCreationIsSupported()

    val top = tempDir.newDirectory("top")
    val file = tempDir.newFile("top/dir1/dir2/dir3/test.txt")
    val link = File(top, "link")
    val fileLink = File(link, "dir3/" + file.name)
    refresh(top)
    watch(top)

    assertEvents({ file.writeText("new content") }, mapOf(file to 'U'))
    assertEvents({ createSymbolicLink(link.toPath(), Paths.get("${top.path}/dir1/dir2")) }, mapOf(link to 'C'))
    refresh(top)
    assertEvents({ file.writeText("newer content") }, mapOf(fileLink to 'U', file to 'U'))
    assertEvents({ link.delete() }, mapOf(link to 'D'))
    assertEvents({ file.writeText("even newer content") }, mapOf(file to 'U'))
  }

  @Ignore("symlink resolution doesn't work over 9P")
  @Test fun testJunctionBelowWatchRoot() {
    val top = tempDir.newDirectory("top")
    val file = tempDir.newFile("top/dir1/dir2/dir3/test.txt")
    val link = createJunction("${top.path}/dir1/dir2", "${top.path}/link")
    val fileLink = File(link, "dir3/" + file.name)
    refresh(top)
    watch(top)

    assertEvents({ file.writeText("new content") }, mapOf(fileLink to 'U', file to 'U'))
    assertEvents({ assertTrue(file.delete()) }, mapOf(fileLink to 'D', file to 'D'))
    assertEvents({ file.writeText("re-creation") }, mapOf(fileLink to 'C', file to 'C'))
  }

  @Ignore("symlink resolution doesn't work over 9P")
  @Test fun testJunctionBelowWatchRootCreation() {
    val top = tempDir.newDirectory("top")
    val file = tempDir.newFile("top/dir1/dir2/dir3/test.txt")
    val link = File(top, "link")
    val fileLink = File(link, "dir3/" + file.name)
    refresh(top)
    watch(top)

    assertEvents({ file.writeText("new content") }, mapOf(file to 'U'))
    assertEvents({ createJunction("${top.path}/dir1/dir2", link.path) }, mapOf(link to 'C'))
    refresh(top)
    assertEvents({ file.writeText("newer content") }, mapOf(fileLink to 'U', file to 'U'))
    assertEvents({ link.delete() }, mapOf(link to 'D'))
    assertEvents({ file.writeText("even newer content") }, mapOf(file to 'U'))
  }

  @Ignore("not yet supported")
  @Test fun testSubst() {
    val target = tempDir.newDirectory("top")
    val file = tempDir.newFile("top/sub/test.txt")

    val substRoot = createSubst(target.path)
    VfsRootAccess.allowRootAccess(testRootDisposable, substRoot.path)
    val vfsRoot = fs.findFileByIoFile(substRoot)!!
    watchedPaths += substRoot.path

    val substFile = File(substRoot, "sub/test.txt")
    refresh(target)
    refresh(substRoot)

    try {
      watch(substRoot)
      assertEvents({ file.writeText("new content") }, mapOf(substFile to 'U'))

      val request = watch(target)
      assertEvents({ file.writeText("updated content") }, mapOf(file to 'U', substFile to 'U'))
      assertEvents({ assertTrue(file.delete()) }, mapOf(file to 'D', substFile to 'D'))
      unwatch(request)

      assertEvents({ file.writeText("re-creation") }, mapOf(substFile to 'C'))
    }
    finally {
      deleteSubst(substRoot.path)
      (vfsRoot as NewVirtualFile).markDirty()
      fs.refresh(false)
    }
  }

  @Test fun testDirectoryRecreation() {
    val root = tempDir.newDirectory("root")
    val dir = tempDir.newDirectory("root/dir")
    val file1 = tempDir.newFile("root/dir/file1.txt")
    val file2 = tempDir.newFile("root/dir/file2.txt")
    refresh(root)

    watch(root)
    assertEvents(
      {
        assertTrue(dir.deleteRecursively())
        assertTrue(dir.mkdir())
        arrayOf(file1, file2).forEach { it.writeText("text") }
      },
      mapOf(file1 to 'U', file2 to 'U'))
  }

  @Test fun testWatchRootRecreation() {
    val root = tempDir.newDirectory("root")
    val file1 = tempDir.newFile("root/file1.txt")
    val file2 = tempDir.newFile("root/file2.txt")
    refresh(root)

    watch(root)
    assertEvents(
      {
        assertTrue(root.deleteRecursively())
        assertTrue(root.mkdir())
        TimeoutUtil.sleep(1500)  // implementation specific
        arrayOf(file1, file2).forEach { it.writeText("text") }
      },
      mapOf(file1 to 'U', file2 to 'U'))
  }

  @Test fun testWatchNonExistingRoot() {
    val top = tempDir.resolve("top").toFile()
    val root = tempDir.resolve("top/d1/d2/d3/root").toFile()
    refresh(tempDir.toFile())

    watch(root)
    assertEvents({ assertTrue(root.mkdirs()) }, mapOf(top to 'C'))
  }

  @Test fun testWatchRootRenameRemove() {
    val top = tempDir.newDirectory("top")
    val root = tempDir.newDirectory("top/d1/d2/d3/root")
    val root2 = File(top, "root2")
    refresh(top)

    watch(root)
    assertEvents({ assertTrue(root.renameTo(root2)) }, mapOf(root to 'D', root2 to 'C'))
    assertEvents({ assertTrue(root2.renameTo(root)) }, mapOf(root to 'C', root2 to 'D'))
    assertEvents({ assertTrue(root.deleteRecursively()) }, mapOf(root to 'D'))
    assertEvents({ assertTrue(root.mkdirs()) }, mapOf(root to 'C'))
    assertEvents({ assertTrue(top.deleteRecursively()) }, mapOf(top to 'D'))
    assertEvents({ assertTrue(root.mkdirs()) }, mapOf(top to 'C'))
  }

  @Test fun testSwitchingToFsRoot() {
    val top = tempDir.newDirectory("top")
    val root = tempDir.newDirectory("top/root")
    val file1 = tempDir.newFile("top/1.txt")
    val file2 = tempDir.newFile("top/root/2.txt")
    refresh(top)
    val fsRoot = top.toPath().root.toFile()
    assertTrue("can't guess root of $top", fsRoot.exists())

    val request = watch(root)
    assertEvents({ arrayOf(file1, file2).forEach { it.writeText("new content") } }, mapOf(file2 to 'U'))

    val rootRequest = watch(fsRoot, checkRoots = false)
    assertEvents({ arrayOf(file1, file2).forEach { it.writeText("12345") } }, mapOf(file1 to 'U', file2 to 'U'), SHORT_PROCESS_DELAY)
    unwatch(rootRequest)

    assertEvents({ arrayOf(file1, file2).forEach { it.writeText("") } }, mapOf(file2 to 'U'))

    unwatch(request)
    assertEvents({ arrayOf(file1, file2).forEach { it.writeText("xyz") } }, mapOf(), SHORT_PROCESS_DELAY)
  }

  // tests the same scenarios with an active file watcher (prevents explicit marking of refreshed paths)
  @Test fun testPartialRefresh(): Unit = LocalFileSystemTest.doTestPartialRefresh(tempDir.newDirectory("top"))
  @Test fun testInterruptedRefresh(): Unit = LocalFileSystemTest.doTestInterruptedRefresh(tempDir.newDirectory("top"))
  @Test fun testRefreshAndFindFile(): Unit = LocalFileSystemTest.doTestRefreshAndFindFile(tempDir.newDirectory("top"))
  @Test fun testRefreshEquality(): Unit = LocalFileSystemTest.doTestRefreshEquality(tempDir.newDirectory("top"))

  @Test fun testUnicodePaths() {
    val name = getUnicodeName()
    assumeTrue("Unicode names not supported", name != null)

    val root = tempDir.newDirectory(name!!)
    val file = tempDir.newFile("${name}/${name}.txt")
    refresh(root)
    watch(root)

    assertEvents({ file.writeText("abc") }, mapOf(file to 'U'))
  }

  @Test fun testDisplacementByIsomorphicTree() {
    val top = tempDir.newDirectory("top")
    val root = tempDir.newDirectory("top/root")
    val file = tempDir.newFile("top/root/middle/file.txt")
    file.writeText("original content")
    val root_copy = File(top, "root_copy")
    root.copyRecursively(root_copy)
    file.writeText("new content")
    val root_bak = File(top, "root.bak")

    val vFile = fs.refreshAndFindFileByIoFile(file)!!
    assertThat(VfsUtilCore.loadText(vFile)).isEqualTo("new content")

    watch(root)
    assertEvents({ Files.move(root.toPath(), root_bak.toPath()); Files.move(root_copy.toPath(), root.toPath()) }, mapOf(file to 'U'))
    assertTrue(vFile.isValid)
    assertThat(VfsUtilCore.loadText(vFile)).isEqualTo("original content")
  }

  @Test fun testWatchRootReplacement() {
    val root1 = tempDir.newDirectory("top/root1")
    val root2 = tempDir.newDirectory("top/root2")
    val file1 = tempDir.newFile("top/root1/file.txt")
    val file2 = tempDir.newFile("top/root2/file.txt")
    refresh(file1)
    refresh(file2)

    val request = watch(root1)
    assertEvents({ arrayOf(file1, file2).forEach { it.writeText("data") } }, mapOf(file1 to 'U'))
    fs.replaceWatchedRoot(request, root2.path, true)
    wait { watcher.isSettingRoots }
    assertEvents({ arrayOf(file1, file2).forEach { it.writeText("more data") } }, mapOf(file2 to 'U'))
  }

  @Ignore("IdeaWin32 incorrectly reads R/O attribute")
  @Test fun testPermissionUpdate() {
    val file = tempDir.newFile("test.txt")
    val vFile = refresh(file)
    assertTrue(vFile.isWritable)
    val localPath = localPath(file)

    watch(file)
    assertEvents({ wsl("chmod", "a-w", localPath) }, mapOf(file to 'P'))
    assertFalse(vFile.isWritable)
    assertEvents({ wsl("chmod", "u+w", localPath) }, mapOf(file to 'P'))
    assertTrue(vFile.isWritable)
  }

  @Test fun testSyncRefreshNonWatchedFile() {
    val file = tempDir.newFile("test.txt")
    val vFile = refresh(file)
    file.writeText("new content")
    assertThat(VfsTestUtil.print(VfsTestUtil.getEvents { vFile.refresh(false, false) })).containsOnly("U : ${vFile.path}")
  }

  //<editor-fold desc="Helpers">
  private fun Path.newDirectory(relativeName: String): File =
    Files.createDirectories(resolve(relativeName)).toFile()

  private fun Path.newFile(relativeName: String): File {
    val file = resolve(relativeName)
    Files.createDirectories(file.parent)
    Files.writeString(file, "")
    return file.toFile()
  }

  private fun watch(file: File, recursive: Boolean = true, checkRoots: Boolean = true): LocalFileSystem.WatchRequest {
    val request = FileWatcherTestUtil.watch(watcher, file, recursive)
    assertThat(watcher.manualWatchRoots).let { if (checkRoots) it.doesNotContain(file.path) else it.contains(file.path) }
    return request
  }

  private fun unwatch(request: LocalFileSystem.WatchRequest) {
    FileWatcherTestUtil.unwatch(watcher, request)
    fs.refresh(false)
  }

  private fun assertEvents(action: () -> Unit, expectedOps: Map<File, Char>, timeout: Long = NATIVE_PROCESS_DELAY) {
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

    val events = VfsTestUtil.getEvents { fs.refresh(false) }.asSequence()
      .filterNot { FileUtil.startsWith(it.path, PathManager.getConfigPath()) || FileUtil.startsWith(it.path, PathManager.getSystemPath()) }
      .filterNot { it is VFilePropertyChangeEvent && it.propertyName == VirtualFile.PROP_CHILDREN_CASE_SENSITIVITY }
      .toList()

    val expected = expectedOps.entries.map { "${it.value} : ${FileUtil.toSystemIndependentName(it.key.path)}" }.sorted()
    val actual = VfsTestUtil.print(events).sorted()
    assertEquals(expected, actual)
  }

  private fun wsl(vararg command: String): Unit = PlatformTestUtil.assertSuccessful(GeneralCommandLine("wsl", "-d", wsl, "-e", *command))

  private fun localPath(file: File): String = '/' + tempDir.root.relativize(file.toPath()).toString().replace('\\', '/')
  //</editor-fold>
}
