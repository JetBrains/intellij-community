// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.wsl

import com.intellij.execution.wsl.sync.WslSync
import com.intellij.testFramework.rules.TempDirectory
import com.intellij.util.io.*
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.Timeout
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.util.concurrent.TimeUnit
import kotlin.io.path.exists
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText

class LinuxTemp(private val wslRule: WslRule) : ExternalResource() {
  lateinit var dir: String
    private set

  override fun before() {
    dir = wslRule.wsl.runCommand("mktemp", "-d")
  }

  override fun after() {
    wslRule.wsl.runCommand("rm", "-rf", dir)
  }
}

@RunWith(Parameterized::class)
class WslSyncTest(private val linToWin: Boolean) : WslTestBase() {

  companion object {
    @Parameters
    @JvmStatic
    fun data(): Collection<Array<Boolean>> = listOf(arrayOf(true), arrayOf(false))
  }

  @JvmField
  @Rule
  val winDirRule = TempDirectory()

  @JvmField
  @Rule
  val linuxDirRule = LinuxTemp(wslRule)

  @JvmField
  @Rule
  val timeoutRule = Timeout(30, TimeUnit.SECONDS)


  private val linuxDirAsPath: Path get() = wslRule.wsl.getUNCRootVirtualFile(true)!!.toNioPath().resolve(linuxDirRule.dir)

  @Test
  fun syncDifferentRegister() {
    val win = winDirRule.newDirectoryPath()
    val sourceDir = if (linToWin) linuxDirAsPath else win
    val newFile = sourceDir.resolve("file.txt").createFile()
    val lastModified = newFile.lastModified()

    if (linToWin) {
      wsl.executeOnWsl(1000, "touch", "${linuxDirRule.dir}/File.txt")
    }
    else {
      val file = sourceDir.resolve("file.txt")
      val data = file.readBytes()
      file.writeBytes(data)
    }
    WslSync.syncWslFolders(linuxDirRule.dir, win, wslRule.wsl, linToWin)
    Assert.assertEquals(sourceDir.resolve(if (linToWin) "file.txt" else "File.txt").lastModified(), lastModified)
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
    val fileNames = (0..numberOfFiles).map { "$it-по-русски.txt" }

    val sourceDir = if (linToWin) linuxDirAsPath else windowsDir
    val destDir = if (linToWin) windowsDir else linuxDirAsPath


    for (fileName in fileNames) {
      sourceDir.resolve(fileName).writeText("hello $fileName")
    }

    WslSync.syncWslFolders(linuxDirRule.dir, windowsDir, wslRule.wsl, linToWin)

    val modificationTimes = mutableMapOf<Path, FileTime>()
    for (fileName in fileNames) {
      val file = destDir.resolve(fileName)
      Assert.assertTrue("File hasn't been copied", file.exists())
      Assert.assertEquals("Copied with wrong content", "hello $fileName", file.readText())
      modificationTimes[file] = file.lastModified()
    }
    Assert.assertEquals(fileNames.size, destDir.toFile().list()!!.size)

    Thread.sleep(1000) // To check modification time

    val fileIdsToModify = fileNames.indices.filter { it % modifyEachFile == 0 }
    for (fileId in fileIdsToModify) {
      sourceDir.resolve(fileNames[fileId]).writeText("Modified")
    }

    WslSync.syncWslFolders(linuxDirRule.dir, windowsDir, wslRule.wsl, linToWin)

    for ((id, fileName) in fileNames.withIndex()) {
      val file = destDir.resolve(fileName)
      if (id in fileIdsToModify) {
        Assert.assertEquals("File not copied", "Modified", file.readText())
        Assert.assertNotEquals("File not modified: $file", modificationTimes[file], file.lastModified())
      }
      else {
        Assert.assertEquals("Content broken", "hello $fileName", file.readText())
        Assert.assertEquals("Wrong file modified: $file", modificationTimes[file], file.lastModified())
      }
    }
  }

  @Test
  fun removeFiles() {
    val windowsDir = winDirRule.newDirectoryPath()

    val sourceDir = if (linToWin) linuxDirAsPath else windowsDir
    val destDir = if (linToWin) windowsDir else linuxDirAsPath

    for (i in (1..5)) {
      sourceDir.resolve("file$i.txt").createFile().writeText("test")
    }
    WslSync.syncWslFolders(linuxDirRule.dir, windowsDir, wslRule.wsl, linToWin)

    Assert.assertTrue("File hasn't been copied", destDir.resolve("file2.txt").exists())
    sourceDir.resolve("file2.txt").delete()
    WslSync.syncWslFolders(linuxDirRule.dir, windowsDir, wslRule.wsl, linToWin)
    Assert.assertFalse("File hasn't been deleted", destDir.resolve("file2.txt").exists())

  }
}