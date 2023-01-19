// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.roots.impl.ProjectFileIndexScopes.EXCLUDED
import com.intellij.openapi.roots.impl.ProjectFileIndexScopes.assertInModule
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.testFramework.HeavyPlatformTestCase
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.rules.ProjectModelExtension
import com.intellij.util.concurrency.Semaphore
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

@TestApplication
class UpdateProjectFileIndexOnVfsChangesTest {
  @JvmField
  @RegisterExtension
  val projectModel: ProjectModelExtension = ProjectModelExtension()

  private val fileIndex
    get() = ProjectFileIndex.getInstance(projectModel.project)

  private lateinit var module: Module
  private lateinit var root: VirtualFile

  @BeforeEach
  fun setUp() {
    module = projectModel.createModule()
    root = projectModel.baseProjectDir.newVirtualDirectory("module")
    ModuleRootModificationUtil.addContentRoot(module, root)
  }

  @Test
  fun `recognize excluded file right after creation`(@TestDisposable disposable: Disposable) {
    val excludedName = "excluded"
    ModuleRootModificationUtil.updateModel(module) { model ->
      model.contentEntries.single().addExcludeFolder("${root.url}/$excludedName")
    }
    val created = ArrayList<VirtualFile>()
    val listener = FileCreationListener(excludedName) { file ->
      fileIndex.assertInModule(file, module, root, EXCLUDED)
      created.add(file)
    }
    projectModel.project.messageBus.connect(disposable).subscribe(VirtualFileManager.VFS_CHANGES, listener)
    val excluded = HeavyPlatformTestCase.createChildDirectory(root, excludedName)
    runReadAction {
      fileIndex.assertInModule(excluded, module, root, EXCLUDED)
    }
    assertEquals(1, created.size, created.toString())
    listener.assertNoErrors()
  }

  @ParameterizedTest(name = "async = {0}")
  @ValueSource(booleans = [false, true])
  fun `recognize excluded file created during refresh`(async: Boolean, @TestDisposable disposable: Disposable) {
    ModuleRootModificationUtil.updateModel(module) { model ->
      model.contentEntries.single().addExcludeFolder("${root.url}/dir/excluded")
    }
    val created: Boolean = File(root.path, "dir/excluded/foo").mkdirs()
    assertTrue(created)

    val listener = FileCreationListener("dir") { file ->
      assertEquals("dir", file.name)
      fileIndex.assertInModule(file.findFileByRelativePath("excluded")!!, module, root, EXCLUDED)
      fileIndex.assertInModule(file.findFileByRelativePath("excluded/foo")!!, module, root, EXCLUDED)
    }
    projectModel.project.messageBus.connect(disposable).subscribe(VirtualFileManager.VFS_CHANGES, listener)
    if (async) {
      val waitFor = Semaphore(1)
      VirtualFileManager.getInstance().asyncRefresh { waitFor.up() }
      val finished = waitFor.waitFor(10000)
      assertTrue(finished)
    }
    else {
      runWriteActionAndWait {
        VirtualFileManager.getInstance().syncRefresh()
      }
    }
    listener.assertNoErrors()
  }

  private class FileCreationListener(private val expectedName: String, private val handler: (VirtualFile) -> Unit) : BulkFileListener {
    @Volatile
    private var error: Throwable? = null

    @Volatile
    private var called = false
    private val allEvents = CopyOnWriteArrayList<VFileEvent>()

    override fun after(events: List<VFileEvent>) {
      try {
        allEvents.addAll(events)
        val event = events.filterIsInstance<VFileCreateEvent>().singleOrNull { it.childName == expectedName } ?: return
        handler(event.file!!)
        called = true
      }
      catch (e: Throwable) {
        error = e
      }
    }

    fun assertNoErrors() {
      if (error != null) {
        return fail(error)
      }
      assertTrue(called, "VFileCreateEvent[$expectedName] not found in $allEvents")
    }
  }
}