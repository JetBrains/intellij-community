// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.newvfs.persistent

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.*
import com.intellij.openapi.vfs.impl.ZipHandlerBase
import com.intellij.openapi.vfs.impl.jar.TimedZipHandler
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.fixtures.BareTestFixtureTestCase
import com.intellij.testFramework.rules.TempDirectory
import com.intellij.util.FileContentUtilCore
import com.intellij.util.io.zip.JBZipFile
import org.junit.Assert
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
  @JvmField
  @Rule
  val myEdtRule = EdtRule()

  @JvmField
  @Rule
  var projectRule = ProjectRule()

  @JvmField
  @Rule
  var tempDir = TempDirectory()

  @JvmField
  @Rule
  var useCrcRule = UseCrcRule()

  private val project: Project
    get() = projectRule.project

  @Test
  @Throws(IOException::class)
  fun testFireEvent() {
    val dir = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(tempDir.newDirectory("vDir"))
    Assert.assertNotNull(dir)
    dir!!.children
    var eventFired = false
    VirtualFileManager.getInstance().addVirtualFileListener(object : VirtualFileListener {
      override fun fileCreated(event: VirtualFileEvent) {
        eventFired= true
      }
    }, testRootDisposable)

    addChild(dir, "x.txt")
    Assert.assertTrue(eventFired)
  }

  @Test
  @Throws(IOException::class)
  fun testRefreshNewFile() {
    val vDir = createDir()

    val allVfsListeners = AllVfsListeners(project)
    vDir.toNioPath().resolve("added.txt").writeText("JB")
    vDir.forceAsyncRefresh()
    allVfsListeners.assertEvents(1)
  }

  @Test
  @Throws(IOException::class)
  fun testRefreshDeletedFile() {
    val vDir = createDir()
    val file = WriteAction.computeAndWait<VirtualFile, IOException> {
      vDir.createChildData(this, "x.txt")
    }
    val nioFile = file.toNioPath()

    val allVfsListeners = AllVfsListeners(project)
    assertTrue { Files.deleteIfExists(nioFile) }
    vDir.forceAsyncRefresh()
    allVfsListeners.assertEvents(1)
  }

  @Test
  @Throws(IOException::class)
  fun testRefreshModifiedFile() {
    val vDir = createDir()
    val file = WriteAction.computeAndWait<VirtualFile, IOException> {
      val child = vDir.createChildData(this, "x.txt")
      VfsUtil.saveText(child, "42.2")
      child
    }
    val nioFile = file.toNioPath()

    val allVfsListeners = AllVfsListeners(project)
    nioFile.writeText("21.1")
    vDir.forceAsyncRefresh()
    allVfsListeners.assertEvents(1)
  }

  @Test
  @Throws(IOException::class)
  fun testAddedJar() {
    doTestAddedJar()
  }

  @Test
  @Throws(IOException::class)
  fun testRemovedJar() {
    doTestRemovedJar()
  }

  @Test
  @Throws(IOException::class)
  fun testMovedJar() {
    doTestMovedJar()
  }

  @Test
  @Throws(IOException::class)
  fun testModifiedJar() {
    doTestModifiedJar()
  }

  @Test
  @Throws(IOException::class)
  fun testAddedJarWithCrc() {
    doTestAddedJar()
  }

  @Test
  @Throws(IOException::class)
  fun testRemovedJarWithCrc() {
    doTestRemovedJar()
  }

  @Test
  @Throws(IOException::class)
  fun testMovedJarWithCrc() {
    doTestMovedJar()
  }

  @Test
  @Throws(IOException::class)
  fun testModifiedJarWithCrc() {
    doTestModifiedJar()
  }

  @Test
  @Throws(IOException::class)
  fun testNestedEventProcessing() {
    val fileForNestedMove = tempDir.newVirtualFile("to-move.txt")
    val nestedMoveTarget = tempDir.newVirtualDirectory("move-target")
    val fireMove = AtomicBoolean(false)
    val movePerformed = AtomicBoolean(false)

    // execute nested event while async refresh below
    project.messageBus.connect(testRootDisposable).subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
      override fun after(events: MutableList<out VFileEvent>) {
        if (fireMove.get() && !movePerformed.get()) {
          movePerformed.set(true)
          PersistentFS.getInstance().moveFile(this, fileForNestedMove, nestedMoveTarget)
        }
      }
    })


    val vDir = createDir()
    val allVfsListeners = AllVfsListeners(project)
    fireMove.set(true)
    assertFalse { movePerformed.get() }

    // execute async refresh
    vDir.toNioPath().resolve("added.txt").writeText("JB")

    vDir.forceAsyncRefresh()

    allVfsListeners.assertEvents(2)
  }

  @Test
  @Throws(IOException::class)
  fun testNestedManualEventProcessing() {
    val fileToReparse = tempDir.newVirtualFile("to-reparse.txt")
    val fireReparse = AtomicBoolean(false)
    val reparsePerformed = AtomicBoolean(false)

    // execute nested event manually via vfs changes message bus publisher
    project.messageBus.connect(testRootDisposable).subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
      override fun after(events: MutableList<out VFileEvent>) {
        if (fireReparse.get() && !reparsePerformed.get()) {
          reparsePerformed.set(true)
          FileContentUtilCore.reparseFiles(fileToReparse)
        }
      }
    })


    val vDir = createDir()
    val allVfsListeners = AllVfsListeners(project)
    fireReparse.set(true)
    assertFalse { reparsePerformed.get() }

    // execute async refresh
    vDir.toNioPath().resolve("added.txt").writeText("JB")

    vDir.forceAsyncRefresh()

    allVfsListeners.assertEvents(2)
  }

  private fun doTestAddedJar() {
    val vDir = createDir()

    val allVfsListeners = AllVfsListeners(project)
    createJar(vDir.toNioPath())

    vDir.forceAsyncRefresh()

    // in case of jar added we don't expect creation events for all of jar's entries.
    allVfsListeners.assertEvents(1)
  }

  private fun doTestRemovedJar() {
    val vDir = createDir()
    val jar = createJar(vDir.toNioPath())
    vDir.forceAsyncRefresh()

    val allVfsListeners = AllVfsListeners(project)
    TimedZipHandler.closeOpenZipReferences()
    assertTrue { Files.deleteIfExists(jar) }
    vDir.forceAsyncRefresh()
    allVfsListeners.assertEvents(2)
  }

  private fun doTestMovedJar() {
    val vDir = createDir()
    val childDir1 = addChild(vDir, "childDir1", true).toNioPath()
    val childDir2 = addChild(vDir, "childDir2", true).toNioPath()
    val jar = createJar(childDir1)
    vDir.forceAsyncRefresh()

    val allVfsListeners = AllVfsListeners(project)
    TimedZipHandler.closeOpenZipReferences()
    jar.moveTo(childDir2.resolve(jar.fileName), true)
    vDir.forceAsyncRefresh()
    allVfsListeners.assertEvents(3)
  }

  private fun doTestModifiedJar() {
    val vDir = createDir()
    val jar = createJar(vDir.toNioPath())
    vDir.forceAsyncRefresh()

    modifyJar(jar)
    val allVfsListeners = AllVfsListeners(project)
    vDir.forceAsyncRefresh()
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

  private fun VirtualFile.forceAsyncRefresh() {
    PlatformTestUtil.waitForFuture(ApplicationManager.getApplication().executeOnPooledThread {
      refresh(false, true)
    }, 30_000)
  }

  private class AllVfsListeners(project: Project) {
    private val asyncEvents: MutableList<VFileEvent> = Collections.synchronizedList(mutableListOf())
    private val bulkEvents: MutableList<VFileEvent> = mutableListOf()

    init {
      VirtualFileManager.getInstance().addAsyncFileListener({ events ->
        asyncEvents.addAll(events)

        object : AsyncFileListener.ChangeApplier {
          override fun afterVfsChange() {
          }
        }
      }, project)

      project.messageBus.connect(project).subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
        override fun after(events: MutableList<out VFileEvent>) {
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