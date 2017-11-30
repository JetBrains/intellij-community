// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.local

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.IoTestUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.*
import com.intellij.openapi.vfs.impl.local.FileWatcher
import com.intellij.openapi.vfs.impl.local.LocalFileSystemImpl
import com.intellij.openapi.vfs.impl.local.NativeFileWatcherImpl
import com.intellij.openapi.vfs.newvfs.NewVirtualFile
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.VfsTestUtil
import com.intellij.testFramework.fixtures.BareTestFixtureTestCase
import com.intellij.testFramework.rules.TempDirectory
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.Alarm
import com.intellij.util.TimeoutUtil
import com.intellij.util.concurrency.Semaphore
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FileWatcherTest : BareTestFixtureTestCase() {
  //<editor-fold desc="Set up / tear down">

  private val LOG: Logger by lazy { Logger.getInstance(NativeFileWatcherImpl::class.java) }

  private val START_STOP_DELAY = 10000L      // time to wait for the watcher spin up/down
  private val INTER_RESPONSE_DELAY = 500L    // time to wait for a next event in a sequence
  private val NATIVE_PROCESS_DELAY = 60000L  // time to wait for a native watcher response
  private val SHORT_PROCESS_DELAY = 5000L    // time to wait when no native watcher response is expected

  private val UNICODE_NAME_1 = "Úñíçødê"
  private val UNICODE_NAME_2 = "Юникоде"

  @Rule @JvmField val tempDir = TempDirectory()

  private lateinit var fs: LocalFileSystem
  private lateinit var root: VirtualFile
  private lateinit var watcher: FileWatcher
  private lateinit var alarm: Alarm

  private val watchedPaths = mutableListOf<String>()
  private val watcherEvents = Semaphore()
  private val resetHappened = AtomicBoolean()

  @Before fun setUp() {
    LOG.debug("================== setting up " + getTestName(false) + " ==================")

    fs = LocalFileSystem.getInstance()
    root = refresh(tempDir.root)

    runInEdtAndWait { VirtualFileManager.getInstance().syncRefresh() }

    alarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, testRootDisposable)

    watcher = (fs as LocalFileSystemImpl).fileWatcher
    assertFalse(watcher.isOperational)
    watchedPaths += tempDir.root.path
    watcher.startup { path ->
      if (path == FileWatcher.RESET || path != FileWatcher.OTHER && watchedPaths.any { path.startsWith(it) }) {
        alarm.cancelAllRequests()
        alarm.addRequest({ watcherEvents.up() }, INTER_RESPONSE_DELAY)
        if (path == FileWatcher.RESET) resetHappened.set(true)
      }
    }
    wait { !watcher.isOperational }

    LOG.debug("================== setting up " + getTestName(false) + " ==================")
  }

  @After fun tearDown() {
    LOG.debug("================== tearing down " + getTestName(false) + " ==================")

    watcher.shutdown()
    wait { watcher.isOperational }

    runInEdtAndWait {
      runWriteAction { root.delete(this) }
      (fs as LocalFileSystemImpl).cleanupForNextTest()
    }

    LOG.debug("================== tearing down " + getTestName(false) + " ==================")
  }

  //</editor-fold>

  @Test fun testWatchRequestConvention() {
    val dir = tempDir.newFolder("dir")
    val r1 = watch(dir)
    val r2 = watch(dir)
    assertFalse(r1 == r2)
  }

  @Test fun testFileRoot() {
    val files = arrayOf(tempDir.newFile("test1.txt"), tempDir.newFile("test2.txt"))
    files.forEach { refresh(it) }
    files.forEach { watch(it, false) }

    assertEvents({ files.forEach { it.writeText("new content") } }, files.map { it to 'U' }.toMap())
    assertEvents({ files.forEach { it.delete() } }, files.map { it to 'D' }.toMap())
    assertEvents({ files.forEach { it.writeText("re-creation") } }, files.map { it to 'C' }.toMap())
  }

  @Test fun testFileRootRecursive() {
    val files = arrayOf(tempDir.newFile("test1.txt"), tempDir.newFile("test2.txt"))
    files.forEach { refresh(it) }
    files.forEach { watch(it, true) }

    assertEvents({ files.forEach { it.writeText("new content") } }, files.map { it to 'U' }.toMap())
    assertEvents({ files.forEach { it.delete() } }, files.map { it to 'D' }.toMap())
    assertEvents({ files.forEach { it.writeText("re-creation") } }, files.map { it to 'C' }.toMap())
  }

  @Test fun testNonCanonicallyNamedFileRoot() {
    assumeTrue(!SystemInfo.isFileSystemCaseSensitive)

    val file = tempDir.newFile("test.txt")
    refresh(file)

    watch(File(file.path.toUpperCase(Locale.US)))
    assertEvents({ file.writeText("new content") }, mapOf(file to 'U'))
    assertEvents({ file.delete() }, mapOf(file to 'D'))
    assertEvents({ file.writeText("re-creation") }, mapOf(file to 'C'))
  }

  @Test fun testDirectoryRecursive() {
    val top = tempDir.newFolder("top")
    val sub = File(top, "sub")
    val file = File(sub, "test.txt")
    refresh(top)

    watch(top)
    assertEvents({ sub.mkdir() }, mapOf(sub to 'C'))
    refresh(sub)
    assertEvents({ file.createNewFile() }, mapOf(file to 'C'))
    assertEvents({ file.writeText("new content") }, mapOf(file to 'U'))
    assertEvents({ file.delete() }, mapOf(file to 'D'))
    assertEvents({ file.writeText("re-creation") }, mapOf(file to 'C'))
  }

  @Test fun testDirectoryFlat() {
    val top = tempDir.newFolder("top")
    val watchedFile = tempDir.newFile("top/test.txt")
    val unwatchedFile = tempDir.newFile("top/sub/test.txt")
    refresh(top)

    watch(top, false)
    assertEvents({ watchedFile.writeText("new content") }, mapOf(watchedFile to 'U'))
    assertEvents({ unwatchedFile.writeText("new content") }, mapOf(), SHORT_PROCESS_DELAY)
  }

  @Test fun testDirectoryMixed() {
    val top = tempDir.newFolder("top")
    val sub = tempDir.newFolder("top/sub2")
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

  @Test fun testIncorrectPath() {
    val root = tempDir.newFolder("root")
    val file = tempDir.newFile("root/file.zip")
    val pseudoDir = File(file, "sub/zip")
    refresh(root)

    watch(pseudoDir, false)
    assertEvents({ file.writeText("new content") }, mapOf(), SHORT_PROCESS_DELAY)
  }

  @Test fun testDirectoryOverlapping() {
    val top = tempDir.newFolder("top")
    val topFile = tempDir.newFile("top/file1.txt")
    val sub = tempDir.newFolder("top/sub")
    val subFile = tempDir.newFile("top/sub/file2.txt")
    val side = tempDir.newFolder("side")
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
      { arrayOf(topFile, subFile, sideFile).forEach { it.delete() } },
      mapOf(topFile to 'D', subFile to 'D', sideFile to 'D'))
  }

  // ensure that flat roots set via symbolic paths behave correctly and do not report dirty files returned from other recursive roots
  @Test fun testSymbolicLinkIntoFlatRoot() {
    val root = tempDir.newFolder("root")
    val cDir = tempDir.newFolder("root/A/B/C")
    val aLink = IoTestUtil.createSymLink("${root.path}/A", "${root.path}/aLink")
    val flatWatchedFile = tempDir.newFile("root/aLink/test.txt")
    val fileOutsideFlatWatchRoot = tempDir.newFile("root/A/B/C/test.txt")
    refresh(root)

    watch(aLink, false)
    watch(cDir, false)
    assertEvents({ flatWatchedFile.writeText("new content") }, mapOf(flatWatchedFile to 'U'))
    assertEvents({ fileOutsideFlatWatchRoot.writeText("new content") }, mapOf(fileOutsideFlatWatchRoot to 'U'))
  }

  @Test fun testMultipleSymbolicLinkPathsToFile() {
    val root = tempDir.newFolder("root")
    val file = tempDir.newFile("root/A/B/C/test.txt")
    val bLink = IoTestUtil.createSymLink("${root.path}/A/B", "${root.path}/bLink")
    val cLink = IoTestUtil.createSymLink("${root.path}/A/B/C", "${root.path}/cLink")
    refresh(root)
    val bFilePath = File(bLink.path, "C/${file.name}")
    val cFilePath = File(cLink.path, file.name)

    watch(bLink)
    watch(cLink)
    assertEvents({ file.writeText("new content") }, mapOf(bFilePath to 'U', cFilePath to 'U'))
    assertEvents({ file.delete() }, mapOf(bFilePath to 'D', cFilePath to 'D'))
    assertEvents({ file.writeText("re-creation") }, mapOf(bFilePath to 'C', cFilePath to 'C'))
  }

  @Test fun testSymbolicLinkAboveWatchRoot() {
    val top = tempDir.newFolder("top")
    val file = tempDir.newFile("top/dir1/dir2/dir3/test.txt")
    val link = IoTestUtil.createSymLink("${top.path}/dir1/dir2", "${top.path}/link")
    val fileLink = File(top, "link/dir3/test.txt")
    refresh(top)

    watch(link)
    assertEvents({ file.writeText("new content") }, mapOf(fileLink to 'U'))
    assertEvents({ file.delete() }, mapOf(fileLink to 'D'))
    assertEvents({ file.writeText("re-creation") }, mapOf(fileLink to 'C'))
  }

  /*
  public void testSymlinkBelowWatchRoot() throws Exception {
    final File targetDir = FileUtil.createTempDirectory("top.", null);
    final File file = FileUtil.createTempFile(targetDir, "test.", ".txt");
    final File linkDir = FileUtil.createTempDirectory("link.", null);
    final File link = new File(linkDir, "link");
    IoTestUtil.createTempLink(targetDir.getPath(), link.getPath());
    final File fileLink = new File(link, file.getName());
    refresh(targetDir);
    refresh(linkDir);

    final LocalFileSystem.WatchRequest request = watch(linkDir);
    try {
      myAccept = true;
      FileUtil.writeToFile(file, "new content");
      assertEvent(VFileContentChangeEvent.class, fileLink.getPath());

      myAccept = true;
      FileUtil.delete(file);
      assertEvent(VFileDeleteEvent.class, fileLink.getPath());

      myAccept = true;
      FileUtil.writeToFile(file, "re-creation");
      assertEvent(VFileCreateEvent.class, fileLink.getPath());
    }
    finally {
      myFileSystem.removeWatchedRoot(request);
      delete(linkDir);
      delete(targetDir);
    }
  }
*/

  @Test fun testSubst() {
    assumeTrue(SystemInfo.isWindows)

    val target = tempDir.newFolder("top")
    val file = tempDir.newFile("top/sub/test.txt")

    val substRoot = IoTestUtil.createSubst(target.path)
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
      assertEvents({ file.delete() }, mapOf(file to 'D', substFile to 'D'))
      unwatch(request)

      assertEvents({ file.writeText("re-creation") }, mapOf(substFile to 'C'))
    }
    finally {
      IoTestUtil.deleteSubst(substRoot.path)
      (vfsRoot as NewVirtualFile).markDirty()
      fs.refresh(false)
    }
  }

  @Test fun testDirectoryRecreation() {
    val root = tempDir.newFolder("root")
    val dir = tempDir.newFolder("root/dir")
    val file1 = tempDir.newFile("root/dir/file1.txt")
    val file2 = tempDir.newFile("root/dir/file2.txt")
    refresh(root)

    watch(root)
    assertEvents(
      { dir.deleteRecursively(); dir.mkdir(); arrayOf(file1, file2).forEach { it.writeText("text") } },
      mapOf(file1 to 'U', file2 to 'U'))
  }

  @Test fun testWatchRootRecreation() {
    val root = tempDir.newFolder("root")
    val file1 = tempDir.newFile("root/file1.txt")
    val file2 = tempDir.newFile("root/file2.txt")
    refresh(root)

    watch(root)
    assertEvents(
      {
        root.deleteRecursively(); root.mkdir()
        if (SystemInfo.isLinux) TimeoutUtil.sleep(1500)  // implementation specific
        arrayOf(file1, file2).forEach { it.writeText("text") }
      },
      mapOf(file1 to 'U', file2 to 'U'))
  }

  @Test fun testWatchNonExistingRoot() {
    val top = File(tempDir.root, "top")
    val root = File(tempDir.root, "top/d1/d2/d3/root")
    refresh(tempDir.root)

    watch(root)
    assertEvents({ root.mkdirs() }, mapOf(top to 'C'))
  }

  @Test fun testWatchRootRenameRemove() {
    val top = tempDir.newFolder("top")
    val root = tempDir.newFolder("top/d1/d2/d3/root")
    val root2 = File(top, "_root")
    refresh(top)

    watch(root)
    assertEvents({ root.renameTo(root2) }, mapOf(root to 'D', root2 to 'C'))
    assertEvents({ root2.renameTo(root) }, mapOf(root to 'C', root2 to 'D'))
    assertEvents({ root.deleteRecursively() }, mapOf(root to 'D'))
    assertEvents({ root.mkdirs() }, mapOf(root to 'C'))
    assertEvents({ top.deleteRecursively() }, mapOf(top to 'D'))
    assertEvents({ root.mkdirs() }, mapOf(top to 'C'))
  }

  @Test fun testSwitchingToFsRoot() {
    val top = tempDir.newFolder("top")
    val root = tempDir.newFolder("top/root")
    val file1 = tempDir.newFile("top/1.txt")
    val file2 = tempDir.newFile("top/root/2.txt")
    refresh(top)
    val fsRoot = File(if (SystemInfo.isUnix) "/" else top.path.substring(0, 3))
    assertTrue(fsRoot.exists(), "can't guess root of " + top)

    val request = watch(root)
    assertEvents({ arrayOf(file1, file2).forEach { it.writeText("new content") } }, mapOf(file2 to 'U'))

    val rootRequest = watch(fsRoot)
    assertEvents({ arrayOf(file1, file2).forEach { it.writeText("12345") } }, mapOf(file1 to 'U', file2 to 'U'), SHORT_PROCESS_DELAY)
    unwatch(rootRequest)

    assertEvents({ arrayOf(file1, file2).forEach { it.writeText("") } }, mapOf(file2 to 'U'))

    unwatch(request)
    assertEvents({ arrayOf(file1, file2).forEach { it.writeText("xyz") } }, mapOf(), SHORT_PROCESS_DELAY)
  }

  @Test fun testLineBreaksInName() {
    assumeTrue(SystemInfo.isUnix)

    val root = tempDir.newFolder("root")
    val file = tempDir.newFile("root/weird\ndir\nname/weird\nfile\nname")
    refresh(root)

    watch(root)
    assertEvents({ file.writeText("abc") }, mapOf(file to 'U'))
  }

  @Test fun testHiddenFiles() {
    assumeTrue(SystemInfo.isWindows)

    val root = tempDir.newFolder("root")
    val file = tempDir.newFile("root/dir/file")
    refresh(root)

    watch(root)
    assertEvents({ IoTestUtil.setHidden(file.path, true) }, mapOf(file to 'P'))
  }

  @Test fun testFileCaseChange() {
    assumeTrue(!SystemInfo.isFileSystemCaseSensitive)

    val root = tempDir.newFolder("root")
    val file = tempDir.newFile("root/file.txt")
    val newFile = File(file.parent, StringUtil.capitalize(file.name))
    refresh(root)

    watch(root)
    assertEvents({ file.renameTo(newFile) }, mapOf(newFile to 'P'))
  }

  // tests the same scenarios with an active file watcher (prevents explicit marking of refreshed paths)
  @Test fun testPartialRefresh() = LocalFileSystemTest.doTestPartialRefresh(tempDir.newFolder("top"))
  @Test fun testInterruptedRefresh() = LocalFileSystemTest.doTestInterruptedRefresh(tempDir.newFolder("top"))
  @Test fun testRefreshAndFindFile() = LocalFileSystemTest.doTestRefreshAndFindFile(tempDir.newFolder("top"))
  @Test fun testRefreshEquality() = LocalFileSystemTest.doTestRefreshEquality(tempDir.newFolder("top"))

  @Test fun testUnicodePaths() {
    val root = tempDir.newFolder(UNICODE_NAME_1)
    val file = tempDir.newFile("${UNICODE_NAME_1}/${UNICODE_NAME_2}.txt")
    refresh(root)
    watch(root)

    assertEvents({ file.writeText("abc") }, mapOf(file to 'U'))
  }

  @Test fun testDisplacementByIsomorphicTree() {
    assumeTrue(!SystemInfo.isMac)

    val top = tempDir.newFolder("top")
    val root = tempDir.newFolder("top/root")
    val file = tempDir.newFile("top/root/middle/file.txt")
    file.writeText("original content")
    val root_copy = File(top, "root_copy")
    root.copyRecursively(root_copy)
    file.writeText("new content")
    val root_bak = File(top, "root.bak")

    val vFile = fs.refreshAndFindFileByIoFile(file)!!
    assertEquals("new content", VfsUtilCore.loadText(vFile))

    watch(root)
    assertEvents({ root.renameTo(root_bak); root_copy.renameTo(root) }, mapOf(file to 'U'))
    assertTrue(vFile.isValid)
    assertEquals("original content", VfsUtilCore.loadText(vFile))
  }

  @Test fun testWatchRootReplacement() {
    val root1 = tempDir.newFolder("top/root1")
    val root2 = tempDir.newFolder("top/root2")
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

  @Test fun testPermissionUpdate() {
    val file = tempDir.newFile("test.txt")
    val vFile = refresh(file)
    assertTrue(vFile.isWritable)
    val ro = if (SystemInfo.isWindows) arrayOf("attrib", "+R", file.path) else arrayOf("chmod", "500", file.path)
    val rw = if (SystemInfo.isWindows) arrayOf("attrib", "-R", file.path) else arrayOf("chmod", "700", file.path)

    watch(file)
    assertEvents({ PlatformTestUtil.assertSuccessful(GeneralCommandLine(*ro)) }, mapOf(file to 'P'))
    assertFalse(vFile.isWritable)
    assertEvents({ PlatformTestUtil.assertSuccessful(GeneralCommandLine(*rw)) }, mapOf(file to 'P'))
    assertTrue(vFile.isWritable)
  }

  @Test fun testSyncRefreshNonWatchedFile() {
    val file = tempDir.newFile("test.txt")
    val vFile = refresh(file)
    file.writeText("new content")
    assertThat(VfsTestUtil.print(VfsTestUtil.getEvents { vFile.refresh(false, false) })).containsOnly("U : ${vFile.path}")
  }

  //<editor-fold desc="Helpers">

  private fun wait(timeout: Long = START_STOP_DELAY, condition: () -> Boolean) {
    val stopAt = System.currentTimeMillis() + timeout
    while (condition()) {
      assertTrue(System.currentTimeMillis() < stopAt, "operation timed out")
      TimeoutUtil.sleep(10)
    }
  }

  private fun watch(file: File, recursive: Boolean = true): LocalFileSystem.WatchRequest {
    val request = fs.addRootToWatch(file.path, recursive)!!
    wait { watcher.isSettingRoots }
    return request
  }

  private fun unwatch(request: LocalFileSystem.WatchRequest) {
    fs.removeWatchedRoot(request)
    wait { watcher.isSettingRoots }
    fs.refresh(false)
  }

  private fun refresh(file: File): VirtualFile {
    val vFile = fs.refreshAndFindFileByIoFile(file)!!
    VfsUtilCore.visitChildrenRecursively(vFile, object : VirtualFileVisitor<Any>() {
      override fun visitFile(file: VirtualFile): Boolean { file.children; return true }
    })
    vFile.refresh(false, true)
    return vFile
  }

  private fun assertEvents(action: () -> Unit, expectedOps: Map<File, Char>, timeout: Long = NATIVE_PROCESS_DELAY) {
    LOG.debug("** waiting for ${expectedOps}")
    watcherEvents.down()
    alarm.cancelAllRequests()
    resetHappened.set(false)

    if (SystemInfo.isWindows || SystemInfo.isMac) TimeoutUtil.sleep(250)
    action()
    LOG.debug("** action performed")

    watcherEvents.waitFor(timeout)
    watcherEvents.up()
    assumeFalse("reset happened", resetHappened.get())
    LOG.debug("** done waiting")

    val events = VfsTestUtil.getEvents { fs.refresh(false) }.filter { !FileUtil.startsWith(it.path, PathManager.getSystemPath()) }

    val expected = expectedOps.entries.map { "${it.value} : ${FileUtil.toSystemIndependentName(it.key.path)}" }.sorted()
    val actual = VfsTestUtil.print(events).sorted()
    assertEquals(expected, actual)
  }

  //</editor-fold>
}