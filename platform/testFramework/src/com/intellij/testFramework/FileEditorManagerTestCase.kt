// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework

import com.intellij.openapi.components.ExpandMacroToPathMap
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.ex.FileEditorProviderManager
import com.intellij.openapi.fileEditor.impl.EditorHistoryManager
import com.intellij.openapi.fileEditor.impl.FileEditorManagerExImpl
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl
import com.intellij.openapi.fileEditor.impl.FileEditorProviderManagerImpl
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.common.runAll
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.docking.DockManager
import com.intellij.util.io.write
import com.intellij.util.ui.EDT
import kotlinx.coroutines.future.asCompletableFuture
import org.jetbrains.jps.model.serialization.PathMacroUtil
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

abstract class FileEditorManagerTestCase : BasePlatformTestCase() {
  @JvmField
  protected var manager: FileEditorManagerImpl? = null
  @JvmField
  protected var initialContainers = 0

  @Throws(Exception::class)
  public override fun setUp() {
    super.setUp()
    manager = FileEditorManagerExImpl(project)
    project.registerComponentInstance(FileEditorManager::class.java, manager!!, testRootDisposable)
    (FileEditorProviderManager.getInstance() as FileEditorProviderManagerImpl).clearSelectedProviders()
    initialContainers = DockManager.getInstance(project).containers.size
  }

  @Throws(Exception::class)
  override fun tearDown() {
    val project = project
    runAll(
      { manager!!.closeAllFiles() },
      { if (project != null) EditorHistoryManager.getInstance(project).removeAllFiles() },
      { (FileEditorProviderManager.getInstance() as FileEditorProviderManagerImpl).clearSelectedProviders() },
      { Disposer.dispose(manager!!) },
      {
        manager = null
        if (project != null) {
          val dockManager = project.getServiceIfCreated(DockManager::class.java)
          val containers = dockManager?.containers ?: emptySet()
          assertSize(initialContainers, containers)
        }
      },
      { super.tearDown() }
    )
  }

  protected fun getFile(path: String): VirtualFile {
    val fullPath = testDataPath + path
    val file = LocalFileSystem.getInstance().refreshAndFindFileByPath(fullPath)
    assertNotNull("Can't find $fullPath", file)
    return file!!
  }

  protected fun createFile(path: String, content: ByteArray): VirtualFile {
    val io = Path.of(testDataPath + path)
    io.write(content)
    val file = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(io)
    assertNotNull("Can't find $io", file)
    return file!!
  }

  protected fun openFiles(femSerialisedText: String) {
    val rootElement = JDOMUtil.load(femSerialisedText)
    val map = ExpandMacroToPathMap()
    map.addMacroExpand(PathMacroUtil.PROJECT_DIR_MACRO_NAME, testDataPath)
    map.substitute(rootElement, true, true)
    manager!!.loadState(rootElement)
    val future = manager!!.mainSplitters.openFilesAsync().asCompletableFuture()
    while (true) {
      try {
        future.get(100, TimeUnit.MILLISECONDS)
        return
      }
      catch (e: TimeoutException) {
        EDT.dispatchAllInvocationEvents()
      }
    }
  }
}