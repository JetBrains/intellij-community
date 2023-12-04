// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.wsl

import com.intellij.execution.wsl.sync.*
import com.intellij.execution.wsl.sync.WslHashFilters.WslHashFiltersBuilder
import com.intellij.execution.wsl.sync.WslHashMatcher.Factory.basename
import com.intellij.execution.wsl.sync.WslHashMatcher.Factory.extension
import com.intellij.execution.wsl.sync.WslHashMatcher.Factory.extensions
import com.intellij.execution.wsl.sync.WslHashMatcher.Factory.fullname
import com.intellij.testFramework.fixtures.TestFixtureRule
import com.intellij.testFramework.rules.TempDirectory
import com.intellij.util.io.createParentDirectories
import com.intellij.util.io.delete
import org.junit.Assert
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.rules.Timeout
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.util.concurrent.TimeUnit
import kotlin.io.path.*

@RunWith(Parameterized::class)
class WslSyncTest(private val linToWin: Boolean) {
  class WslTempDirectory(private val wslRule: WslRule) : ExternalResource() {
    lateinit var dir: String
      private set

    override fun before() {
      dir = wslRule.wsl.runCommand("mktemp", "-d").getOrThrow()
    }

    override fun after() {
      wslRule.wsl.runCommand("rm", "-rf", dir)
    }
  }

  companion object {
    @Parameters(name = "linToWin={0}")
    @JvmStatic
    fun data(): Collection<Array<Boolean>> = listOf(arrayOf(true), arrayOf(false))

    private val appRule = TestFixtureRule()
    private val wslRule = WslRule()

    @ClassRule
    @JvmField
    val ruleChain: RuleChain = RuleChain.outerRule(appRule).around(wslRule)
    private fun createAndGetLinks(storage: FileStorage<*, *>,
                                  target: FilePathRelativeToDir,
                                  vararg sources: FilePathRelativeToDir): Map<FilePathRelativeToDir, FilePathRelativeToDir> {
      for (source in sources) {
        storage.createSymLinks(mapOf(Pair(source, target)))
      }
      return storage.calculateSyncData().links
    }
  }

  @JvmField
  @Rule
  val winDirRule = TempDirectory()

  @JvmField
  @Rule
  val linuxDirRule = WslTempDirectory(wslRule)

  @JvmField
  @Rule
  val timeoutRule = Timeout(30, TimeUnit.SECONDS)

  private val linuxDirAsPath: Path
    get() = wslRule.wsl.getUNCRootPath().resolve(linuxDirRule.dir)

  @Test
  fun testLinksReported() {
    val sources = arrayOf("source1", "another_source").map { FilePathRelativeToDir(it) }.toTypedArray()
    val storage: FileStorage<*, *>
    if (linToWin) {
      val dir = linuxDirRule.dir
      wslRule.wsl.executeOnWsl(1000, "mkdir", "${dir}/target")
      storage = LinuxFileStorage(dir, wslRule.wsl)
    }
    else {
      val dir = winDirRule.newDirectoryPath()
      val targetDir = dir.resolve("target")
      targetDir.createDirectory()
      storage = WindowsFileStorage(dir, wslRule.wsl)
    }

    val links = createAndGetLinks(storage, FilePathRelativeToDir("target"), *sources)
    for (source in sources) {
      Assert.assertEquals(FilePathRelativeToDir("target"), links[source])
    }
  }

  @Test
  fun testNonExistingLinuxDir() {
    Assert.assertTrue("Folder must be empty", LinuxFileStorage("/etc/foobarbuz", wslRule.wsl).isEmpty())
  }
  @Test
  fun testLinks() {
    val sources = arrayOf("source", "source_2").map { FilePathRelativeToDir(it) }.toTypedArray()
    val from: FileStorage<*, *>
    val to: FileStorage<*, *>
    val winRoot = winDirRule.newDirectoryPath()
    val linRoot = linuxDirRule.dir
    val win = WindowsFileStorage(winRoot, wslRule.wsl)
    val lin = LinuxFileStorage(linRoot, wslRule.wsl)
    if (!linToWin) {
      winRoot.resolve("target dir").createDirectory().resolve("file.txt").createParentDirectories().createFile()
      winRoot.resolve("dir_to_ignore").createDirectory()
      from = win
      to = lin
    }
    else {
      wslRule.wsl.runCommand("mkdir", "$linRoot/target dir")
      wslRule.wsl.runCommand("mkdir", "$linRoot/dir_to_ignore")
      wslRule.wsl.runCommand("touch", "$linRoot/target dir/file.txt")
      from = lin
      to = win
    }
    for (source in sources) {
      from.createSymLinks(mapOf(Pair(source, FilePathRelativeToDir("target dir"))))
    }
    to.createSymLinks(mapOf(Pair(FilePathRelativeToDir("dir_to_ignore/foo"), FilePathRelativeToDir("target dir"))))
    WslSync.syncWslFolders(linRoot, winRoot, wslRule.wsl, linToWinCopy = linToWin)
    val links = to.calculateSyncData().links
    for (source in sources) {
      Assert.assertEquals("target dir", links[source]?.asWindowsPath)
    }
    to.createSymLinks(mapOf(Pair(FilePathRelativeToDir("remove_me"), FilePathRelativeToDir("target dir"))))
    WslSync.syncWslFolders(linRoot, winRoot, wslRule.wsl, linToWinCopy = linToWin)
    Assert.assertEquals(null, to.calculateSyncData().links[FilePathRelativeToDir("remove_me")])
    for (source in sources) {
      Assert.assertEquals("target dir", links[source]?.asWindowsPath)
    }
  }

  /**
   * Create lowercase file on Linux and uppercase on Windows.
   * Touch linux file, and see it is NOT copied since content is the same (although time differs)
   */
  @Test
  fun syncDifferentRegister() {
    val win = winDirRule.newDirectoryPath()

    linuxDirAsPath.resolve("file.txt").createParentDirectories().createFile()
    val destFile = win.resolve("File.txt").createParentDirectories().createFile()
    val modTime = destFile.getLastModifiedTime()

    Thread.sleep(100)
    wslRule.wsl.executeOnWsl(1000, "touch", "${linuxDirRule.dir}/${destFile.name}")

    WslSync.syncWslFolders(linuxDirRule.dir, win, wslRule.wsl, true)
    Assert.assertEquals(destFile.getLastModifiedTime(), modTime)
  }

  @Test
  fun syncEmptyFolder() {
    val windowsDir = winDirRule.newDirectoryPath()
    WslSync.syncWslFolders(linuxDirRule.dir, windowsDir, wslRule.wsl, linToWin)
    Assert.assertTrue(windowsDir.toFile().list()!!.isEmpty())
    Assert.assertTrue(linuxDirAsPath.toFile().list()!!.isEmpty())
  }

  @Test
  fun syncFullThenChange() {
    val numberOfFiles = 100
    val modifyEachFile = 3

    val windowsDir = winDirRule.newDirectoryPath()
    val executable = "execfile"
    val execTxt = "exec.txt"
    val fileNames = (0..numberOfFiles).map { "$it-по-русски.txt" }

    val srcDir = if (linToWin) linuxDirAsPath else windowsDir
    val dstDir = if (linToWin) windowsDir else linuxDirAsPath

    for (fileName in fileNames) {
      srcDir.resolve(fileName).writeText("hello $fileName")
    }
    srcDir.resolve(executable).writeText("#!/bin/sh")
    srcDir.resolve(execTxt).writeText(" $executable   ") // Must be marked executable on Linux

    WslSync.syncWslFolders(linuxDirRule.dir, windowsDir, wslRule.wsl, linToWin)
    if (!linToWin) {
      wslRule.wsl.runCommand("${linuxDirRule.dir}/$executable").getOrThrow()
    }

    val modificationTimes = mutableMapOf<Path, FileTime>()
    for (fileName in fileNames) {
      val file = dstDir.resolve(fileName)
      Assert.assertTrue("File hasn't been copied", file.exists())
      Assert.assertEquals("Copied with wrong content", "hello $fileName", file.readText())
      modificationTimes[file] = file.getLastModifiedTime()
    }
    Assert.assertEquals(fileNames.size + 2, dstDir.toFile().list()!!.size) // +  exec files

    Thread.sleep(1000) // To check modification time

    val fileIdsToModify = fileNames.indices.filter { it % modifyEachFile == 0 }
    for (fileId in fileIdsToModify) {
      srcDir.resolve(fileNames[fileId]).writeText("Modified")
    }

    WslSync.syncWslFolders(linuxDirRule.dir, windowsDir, wslRule.wsl, linToWin)

    for ((id, fileName) in fileNames.withIndex()) {
      val file = dstDir.resolve(fileName)
      if (id in fileIdsToModify) {
        Assert.assertEquals("File not copied", "Modified", file.readText())
        Assert.assertNotEquals("File not modified: $file", modificationTimes[file], file.getLastModifiedTime())
      }
      else {
        Assert.assertEquals("Content broken", "hello $fileName", file.readText())
        Assert.assertEquals("Wrong file modified: $file", modificationTimes[file], file.getLastModifiedTime())
      }
    }
  }

  @Test
  fun removeFiles() {
    val windowsDir = winDirRule.newDirectoryPath()
    val srcDir = if (linToWin) linuxDirAsPath else windowsDir
    val dstDir = if (linToWin) windowsDir else linuxDirAsPath

    for (i in (1..5)) {
      srcDir.resolve("file$i.txt").createParentDirectories().createFile().writeText("test")
    }
    WslSync.syncWslFolders(linuxDirRule.dir, windowsDir, wslRule.wsl, linToWin)

    Assert.assertTrue("File hasn't been copied", dstDir.resolve("file2.txt").exists())
    srcDir.resolve("file2.txt").delete()
    WslSync.syncWslFolders(linuxDirRule.dir, windowsDir, wslRule.wsl, linToWin)
    Assert.assertFalse("File hasn't been deleted", dstDir.resolve("file2.txt").exists())
  }

  @Test
  fun syncWithExcludesOnly() {
    doSyncAndAssertFilePresence(
      setOf("файл.dll", "файл.tar.gz", "файл.zip", "debug", "debug.out", "idea.log"),
      setOf("файл.py", "файл.java", "файл-dll", "ddebug", "idea.log.bck"),
      WslHashFiltersBuilder()
        .exclude(*extensions("dll", "gz", "zip"),
                 basename("debug"),
                 fullname("idea.log"))
        .build(),
      false
    )
  }

  @Test
  fun syncWithIncludesOnly() {
    doSyncAndAssertFilePresence(
      setOf("файл.py", "файл.java", "файл-dll", "ddebug", "idea.log.bck"),
      setOf("файл.dll", "файл.tar.gz", "файл.zip", "debug", "debug.out", "idea.log"),
      WslHashFiltersBuilder()
        .include(*extensions("dll", "gz", "zip"),
                 basename("debug"),
                 fullname("idea.log"))
        .build(),
      false
    )
  }

  @Test
  fun syncWithExcludesAndIncludes() {
    doSyncAndAssertFilePresence(
      setOf("файл.dll", "файл.gz", "файл.zip", "debug", "debug.out", "idea.log"),
      setOf("файл.py", "файл.tar.gz", "файл.java", "файл-dll", "ddebug", "debug.in", "idea.log.bck"),
      WslHashFiltersBuilder()
        .exclude(*extensions("dll", "gz", "zip"),
                 basename("debug"),
                 fullname("idea.log"))
        .include(extension("tar.gz"),
                 fullname("debug.in"))
        .build(),
      false
    )
  }

  @Test
  fun syncWithExcludesAndStubs() {
    doSyncAndAssertFilePresence(
      setOf(),
      setOf("файл.dll", "файл.gz", "файл.zip", "debug", "debug.out", "idea.log",
            "файл.py", "файл.tar.gz", "файл.java", "файл-dll", "ddebug", "debug.in", "idea.log.bck"),
      WslHashFiltersBuilder()
        .exclude(*extensions("dll", "gz", "zip"),
                 basename("debug"),
                 fullname("idea.log"))
        .build(),
      true
    )
  }

  @Test
  fun syncWithIncludesAndStubs() {
    doSyncAndAssertFilePresence(
      setOf(),
      setOf("файл.dll", "файл.gz", "файл.zip", "debug", "debug.out", "idea.log",
            "файл.py", "файл.tar.gz", "файл.java", "файл-dll", "ddebug", "debug.in", "idea.log.bck"),
      WslHashFiltersBuilder()
        .include(*extensions("dll", "gz", "zip"),
                 basename("debug"),
                 fullname("idea.log"))
        .build(),
      true
    )
  }

  private fun doSyncAndAssertFilePresence(fileNamesToIgnore: Set<String>,
                                          fileNamesToSync: Set<String>,
                                          filters: WslHashFilters,
                                          useStubs: Boolean) {
    val windowsDir = winDirRule.newDirectoryPath()
    val allFileNames = fileNamesToSync + fileNamesToIgnore

    val srcDir = if (linToWin) linuxDirAsPath else windowsDir
    val dstDir = if (linToWin) windowsDir else linuxDirAsPath

    for (fileName in allFileNames) {
      srcDir.resolve(fileName).writeText("hello $fileName")
    }

    WslSync.syncWslFolders(linuxDirRule.dir, windowsDir, wslRule.wsl, linToWin, filters, useStubs)

    for (fileName in allFileNames) {
      val file = dstDir.resolve(fileName)
      if (fileNamesToIgnore.contains(fileName)) {
        Assert.assertFalse("File ${file} must not be copied", file.exists())
      }
      else {
        Assert.assertTrue("File ${file} must be copied", file.exists())
        if (useStubs && !filters.isFileNameOk(file.fileName.toString())) {
          Assert.assertEquals("File ${file} must be stubbed", "", file.readText().trim())
        }
      }
    }
    Assert.assertEquals("Not all files synced", fileNamesToSync.size, dstDir.toFile().list()!!.size)
  }
}
