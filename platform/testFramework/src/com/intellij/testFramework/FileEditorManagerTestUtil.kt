// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework

import com.intellij.openapi.components.ExpandMacroToPathMap
import com.intellij.openapi.fileEditor.impl.EditorSplitterState
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.io.write
import org.jetbrains.annotations.TestOnly
import org.jetbrains.jps.model.serialization.PathMacroUtil
import java.nio.file.Path

/**
 * File helpers for a JUnit 5 test that uses `projectFixture().fileEditorManagerFixture()`.
 * This is the JUnit 5 replacement of the helpers of [FileEditorManagerTestCase].
 */
@TestOnly
object FileEditorManagerTestUtil {
  /**
   * Finds the file `testDataPath + path` in the local file system after a refresh.
   * Fails when the file does not exist.
   * Call it from any thread.
   */
  @JvmStatic
  fun getFile(testDataPath: String, path: String): VirtualFile {
    val fullPath = testDataPath + path
    return checkNotNull(StandardFileSystems.local().refreshAndFindFileByPath(fullPath)) { "Can't find $fullPath" }
  }

  /**
   * Writes [content] to the file [path] in the temporary directory, and returns the refreshed file.
   * Call it from any thread.
   */
  @JvmStatic
  fun createTempFile(path: String, content: ByteArray): VirtualFile {
    val io = Path.of(FileUtil.getTempDirectory(), path)
    io.write(content)
    return checkNotNull(VirtualFileManager.getInstance().refreshAndFindFileByNioPath(io)) { "Can't find $io" }
  }

  /**
   * Restores the editors of the serialized `FileEditorManager` state [femSerialisedText] in [manager].
   * The `$PROJECT_DIR$` macro expands to [testDataPath].
   * Waits until each restored editor is available.
   * Call it on the EDT.
   */
  @JvmStatic
  @RequiresEdt
  fun openFiles(manager: FileEditorManagerImpl, testDataPath: String, femSerialisedText: String) {
    val rootElement = JDOMUtil.load(femSerialisedText)
    val map = ExpandMacroToPathMap()
    map.addMacroExpand(PathMacroUtil.PROJECT_DIR_MACRO_NAME, testDataPath)
    map.substitute(rootElement, true, true)
    runWithModalProgressBlocking(manager.project, "") {
      manager.mainSplitters.restoreEditors(EditorSplitterState(rootElement))
      manager.mainSplitters.windows().flatMap { it.composites() }.forEach {
        it.waitForAvailable()
      }
    }
  }
}
