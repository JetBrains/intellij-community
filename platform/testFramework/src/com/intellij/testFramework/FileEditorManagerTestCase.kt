// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework

import com.intellij.ide.impl.OpenProjectTask
import com.intellij.openapi.components.ComponentManagerEx
import com.intellij.openapi.components.ExpandMacroToPathMap
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.ex.FileEditorProviderManager
import com.intellij.openapi.fileEditor.impl.EditorHistoryManager
import com.intellij.openapi.fileEditor.impl.EditorSplitterState
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl
import com.intellij.openapi.fileEditor.impl.FileEditorProviderManagerImpl
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.platform.util.coroutines.childScope
import com.intellij.testFramework.common.runAll
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.docking.DockContainer
import com.intellij.ui.docking.DockManager
import com.intellij.util.io.write
import org.jetbrains.jps.model.serialization.PathMacroUtil
import java.nio.file.Path

private val CUSTOM_PROJECT_DESCRIPTOR = object : LightProjectDescriptor() {
  override fun getOpenProjectOptions(): OpenProjectTask {
    return OpenProjectTask {
      beforeInit = { it.putUserData(FileEditorManagerImpl.ALLOW_IN_LIGHT_PROJECT, true) }
    }
  }
}

abstract class FileEditorManagerTestCase : BasePlatformTestCase() {
  @JvmField
  protected var manager: FileEditorManagerImpl? = null

  public override fun setUp() {
    super.setUp()

    val project = project
    project.putUserData(FileEditorManagerImpl.ALLOW_IN_LIGHT_PROJECT, true)
    manager = FileEditorManagerImpl(project, (project as ComponentManagerEx).getCoroutineScope().childScope())
    project.replaceService(FileEditorManager::class.java, manager!!, testRootDisposable)
    (FileEditorProviderManager.getInstance() as FileEditorProviderManagerImpl).clearSelectedProviders()
    check(DockManager.getInstance(project).containers.size == 1) {
      "The previous test didn't clear the state"
    }
  }

  // force light project recreation
  override fun getProjectDescriptor(): LightProjectDescriptor = CUSTOM_PROJECT_DESCRIPTOR

  @Throws(Exception::class)
  override fun tearDown() {
    val project = project
    runAll(
      { manager?.closeAllFiles() },
      { if (project != null) EditorHistoryManager.getInstance(project).removeAllFiles() },
      { (FileEditorProviderManager.getInstance() as FileEditorProviderManagerImpl).clearSelectedProviders() },
      { manager?.let { Disposer.dispose(it) } },
      {
        manager = null
        if (project != null) {
          assertSize(1, project.serviceIfCreated<DockManager>()?.containers ?: emptySet<DockContainer>())
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
    runWithModalProgressBlocking(project, "") {
      manager!!.mainSplitters.restoreEditors(EditorSplitterState(rootElement))
    }
  }
}