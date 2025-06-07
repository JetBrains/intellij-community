// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.vfs.*
import com.intellij.openapi.vfs.impl.jar.JarFileSystemImpl
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.fixtures.BareTestFixtureTestCase
import com.intellij.testFramework.rules.TempDirectory
import com.intellij.util.FileContentUtilCore
import com.intellij.util.io.zip.JBZipFile
import com.intellij.util.messages.MessageBusConnection
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.Description
import org.junit.runners.model.Statement
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.io.path.moveTo
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunsInEdt
class VfsEventsTest : BareTestFixtureTestCase() {
  @JvmField @Rule val edtRule = EdtRule()
  @JvmField @Rule val tempDir = TempDirectory()
  @JvmField @Rule val useCrcRule = UseCrcRule()

  private fun connectToAppBus(): MessageBusConnection =
    ApplicationManager.getApplication().messageBus.connect(testRootDisposable)

  @Test fun testFireEvent() {
    val dir = createDir()

    var eventFired = false
    connectToAppBus().subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
      override fun after(events: List<VFileEvent>) {
        eventFired = eventFired || events.any { it is VFileCreateEvent }
      }
    })

    addChild(dir, "x.txt")
    assertTrue(eventFired)
  }

  @Test fun testRefreshNewFile() {
    val vDir = createDir()

    val allVfsListeners = AllVfsListeners(testRootDisposable)
    vDir.toNioPath().resolve("added.txt").writeText("JB")
    vDir.syncRefresh()
    allVfsListeners.assertEvents(1)
  }

  @Test fun testRefreshDeletedFile() {
    val vDir = createDir()
    val file = WriteAction.computeAndWait<VirtualFile, IOException> {
      vDir.createChildData(this, "x.txt")
    }
    val nioFile = file.toNioPath()

    val allVfsListeners = AllVfsListeners(testRootDisposable)
    assertTrue { Files.deleteIfExists(nioFile) }
    vDir.syncRefresh()
    allVfsListeners.assertEvents(1)
  }

  @Test fun testRefreshModifiedFile() {
    val vDir = createDir()
    val file = WriteAction.computeAndWait<VirtualFile, IOException> {
      val child = vDir.createChildData(this, "x.txt")
      VfsUtil.saveText(child, "42.2")
      child
    }
    val nioFile = file.toNioPath()

    val allVfsListeners = AllVfsListeners(testRootDisposable)
    nioFile.writeText("21.12")
    vDir.syncRefresh()
    allVfsListeners.assertEvents(1)
  }

  @Test fun testAddedJar() = doTestAddedJar()
  @Test fun testRemovedJar() = doTestRemovedJar()
  @Test fun testMovedJar() = doTestMovedJar()
  @Test fun testModifiedJar() = doTestModifiedJar()

  @Test fun testAddedJarWithCrc() = doTestAddedJar()
  @Test fun testRemovedJarWithCrc() = doTestRemovedJar()
  @Test fun testMovedJarWithCrc() = doTestMovedJar()
  @Test fun testModifiedJarWithCrc() = doTestModifiedJar()

  @Test fun testNestedEventProcessing() {
    val fileForNestedMove = tempDir.newVirtualFile("to-move.txt")
    val nestedMoveTarget = tempDir.newVirtualDirectory("move-target")
    val fireMove = AtomicBoolean(false)
    val movePerformed = AtomicBoolean(false)

    // execute nested event while async refresh below
    connectToAppBus().subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
      override fun after(events: List<VFileEvent>) {
        if (fireMove.get() && !movePerformed.get()) {
          movePerformed.set(true)
          PersistentFS.getInstance().moveFile(this, fileForNestedMove, nestedMoveTarget)
        }
      }
    })

    val vDir = createDir()
    val allVfsListeners = AllVfsListeners(testRootDisposable)
    fireMove.set(true)
    assertFalse { movePerformed.get() }

    // execute async refresh
    vDir.toNioPath().resolve("added.txt").writeText("JB")

    vDir.syncRefresh()

    allVfsListeners.assertEvents(2)
  }

  @Test fun testNestedManualEventProcessing() {
    val fileToReparse = tempDir.newVirtualFile("to-reparse.txt")
    val fireReparse = AtomicBoolean(false)
    val reparsePerformed = AtomicBoolean(false)

    // execute nested event manually via vfs changes message bus publisher
    connectToAppBus().subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
      override fun after(events: List<VFileEvent>) {
        if (fireReparse.get() && !reparsePerformed.get()) {
          reparsePerformed.set(true)
          FileContentUtilCore.reparseFiles(fileToReparse)
        }
      }
    })

    val vDir = createDir()
    val allVfsListeners = AllVfsListeners(testRootDisposable)
    fireReparse.set(true)
    assertFalse { reparsePerformed.get() }

    // execute async refresh
    vDir.toNioPath().resolve("added.txt").writeText("JB")

    vDir.syncRefresh()

    allVfsListeners.assertEvents(2)
  }

  private fun doTestAddedJar() {
    val vDir = createDir()

    val allVfsListeners = AllVfsListeners(testRootDisposable)
    createJar(vDir.toNioPath())

    vDir.syncRefresh()

    // in case of jar added we don't expect creation events for all of jar's entries.
    allVfsListeners.assertEvents(1)
  }

  private fun doTestRemovedJar() {
    val vDir = createDir()
    val jar = createJar(vDir.toNioPath())
    vDir.syncRefresh()

    val allVfsListeners = AllVfsListeners(testRootDisposable)
    JarFileSystemImpl.cleanupForNextTest()
    assertTrue { Files.deleteIfExists(jar) }
    vDir.syncRefresh()
    allVfsListeners.assertEvents(2)
  }

  private fun doTestMovedJar() {
    val vDir = createDir()
    val childDir1 = addChild(vDir, "childDir1", true).toNioPath()
    val childDir2 = addChild(vDir, "childDir2", true).toNioPath()
    val jar = createJar(childDir1)
    vDir.syncRefresh()

    val allVfsListeners = AllVfsListeners(testRootDisposable)
    JarFileSystemImpl.cleanupForNextTest()
    jar.moveTo(childDir2.resolve(jar.fileName), true)
    vDir.syncRefresh()
    allVfsListeners.assertEvents(3)
  }

  private fun doTestModifiedJar() {
    val vDir = createDir()
    val jar = createJar(vDir.toNioPath())
    vDir.syncRefresh()

    modifyJar(jar)
    val allVfsListeners = AllVfsListeners(testRootDisposable)
    vDir.syncRefresh()
    allVfsListeners.assertEvents(if(useCrcRule.useCrcForTimestamp) 3 else 4)
  }

  private fun addChild(dir: VirtualFile, name: String, directory: Boolean = false): VirtualFile {
    return WriteAction.computeAndWait<VirtualFile, IOException> {
      if (directory) {
        dir.createChildDirectory(this, name)
      }
      else {
        dir.createChildData(this, name)
      }
    }
  }

  private fun createDir(): VirtualFile {
    val dir = tempDir.newDirectory("vDir")
    val vDir = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(dir)!!
    vDir.children
    return vDir
  }

  private fun createJar(directory: Path): Path {
    val jarPath = directory.resolve("Test.jar")
    JBZipFile(jarPath.toFile()).use {
      it.getOrCreateEntry("awesome.txt").setData("Hello!".toByteArray(Charsets.UTF_8), 666L)
      it.getOrCreateEntry("readme.txt").setData("Read it!".toByteArray(Charsets.UTF_8), 777L)
    }

    var jarFileCount = 0
    //ensure we have file system
    val virtualFile = VfsUtil.findFile(jarPath, true)!!
    val jarRoot = JarFileSystem.getInstance().getJarRootForLocalFile(virtualFile)
    VfsUtil.visitChildrenRecursively(jarRoot!!, object : VirtualFileVisitor<Any>() {
      override fun visitFile(file: VirtualFile): Boolean {
        jarFileCount++
        return true
      }
    })
    assertTrue("Generated jar is empty") { jarFileCount > 0 }

    return jarPath
  }

  private fun modifyJar(jarPath: Path) {
    assertTrue { Files.exists(jarPath) }
    JBZipFile(jarPath.toFile()).use {
      // modify
      it.getOrCreateEntry("awesome.txt").setData("Hello_modified!".toByteArray(Charsets.UTF_8), 666L)
      // add
      it.getOrCreateEntry("readme_2.txt").setData("Read it!".toByteArray(Charsets.UTF_8), 777L)
    }
  }

  private fun VirtualFile.syncRefresh() = refresh(false, true)

  private class AllVfsListeners(disposable: Disposable) {
    private val asyncEvents: MutableList<VFileEvent> = Collections.synchronizedList(mutableListOf())
    private val bulkEvents: MutableList<VFileEvent> = mutableListOf()

    init {
      VirtualFileManager.getInstance().addAsyncFileListener(
        { events ->
          object : AsyncFileListener.ChangeApplier {
            override fun afterVfsChange() {
              asyncEvents.addAll(events)
            }
          }
        }, disposable)

      ApplicationManager.getApplication().messageBus.connect(disposable).subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
        override fun after(events: List<VFileEvent>) {
          bulkEvents.addAll(events)
        }
      })
    }

    fun resetEvents() {
      asyncEvents.clear()
      bulkEvents.clear()
    }

    fun assertEvents(expectedEventCount: Int) {
      val asyncEventsSize = asyncEvents.size
      val bulkEventsSize = bulkEvents.size

      val asyncEventsToString = asyncEvents.joinToString("\n    ") { it.toString() }
      val bulkEventsToString = bulkEvents.joinToString("\n    ") { it.toString() }
      assertEquals(asyncEventsSize, bulkEventsSize, "Async & bulk listener events mismatch." +
                                                    "\n  Async events : $asyncEventsToString," +
                                                    "\n  Bulk events: $bulkEventsToString")
      assertEquals(expectedEventCount, asyncEventsSize, "Unexpected VFS event count received by async listener: $asyncEventsToString")

      assertEquals(asyncEvents.toSet(), bulkEvents.toSet(), "Async & bulk listener events mismatch")

      resetEvents()
    }
  }

  class UseCrcRule : ExternalResource() {
    var useCrcForTimestamp = false

    override fun apply(base: Statement, description: Description): Statement {
      useCrcForTimestamp = description.methodName.endsWith("WithCrc")
      return super.apply(base, description)
    }

    override fun before() {
      if (useCrcForTimestamp) {
        System.setProperty("zip.handler.uses.crc.instead.of.timestamp", true.toString())
      }
    }

    override fun after() {
      if (useCrcForTimestamp) {
        System.setProperty("zip.handler.uses.crc.instead.of.timestamp", false.toString())
      }
    }
  }
}
