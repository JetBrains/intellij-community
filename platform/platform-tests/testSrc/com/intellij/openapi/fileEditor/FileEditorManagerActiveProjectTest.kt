// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor

import com.intellij.ide.impl.OpenProjectTask
import com.intellij.mock.MockProject
import com.intellij.openapi.Disposable
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl
import com.intellij.openapi.fileEditor.impl.FileEditorOpenOptions
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ex.WindowManagerEx
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.fileEditorManagerFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import kotlin.io.path.writeText

/**
 * An open with the focus makes its project the active project of the frame, see `WindowManagerEx.setActiveProject`.
 *
 * A frame with guests shows the editors of one project at a time. The test project owns the frame, and a mock project
 * is its guest, so the frame shows the guest until an editor of the owner takes the focus.
 */
@TestApplication
@Timeout(30)
class FileEditorManagerActiveProjectTest {
  @TestDisposable
  private lateinit var disposable: Disposable

  private val projectFixture = projectFixture(
    openProjectTask = OpenProjectTask {
      beforeInitTasks += { it.putUserData(FileEditorManagerKeys.ALLOW_IN_LIGHT_PROJECT, true) }
    },
    openAfterCreation = true,
  )
  private val fileEditorManagerFixture = projectFixture.fileEditorManagerFixture()
  private val tempDirFixture = tempPathFixture()

  private val project: Project
    get() = projectFixture.get()

  private val manager: FileEditorManagerImpl
    get() = fileEditorManagerFixture.get()

  private val windowManager: WindowManagerEx
    get() = WindowManagerEx.getInstanceEx()

  private lateinit var guest: Project

  @BeforeEach
  fun joinGuest() {
    guest = MockProject(null, disposable)
    windowManager.addFrameGuest(project, guest)
    windowManager.setActiveProject(guest)
    assertSame(guest, windowManager.getActiveProject(project))
  }

  @AfterEach
  fun leaveGuest() {
    windowManager.removeFrameGuest(guest)
  }

  @Test
  fun `an open with the focus makes the project active`(): Unit = timeoutRunBlocking {
    val file = createFile("focused.txt")

    manager.openFile(file, FileEditorOpenOptions(requestFocus = true))

    assertSame(project, windowManager.getActiveProject(project), "The frame shows the project whose editor takes the focus")
  }

  @Test
  fun `a second open of an open tab with the focus makes the project active again`(): Unit = timeoutRunBlocking {
    val file = createFile("reopened.txt")
    manager.openFile(file, FileEditorOpenOptions(requestFocus = true))
    windowManager.setActiveProject(guest)

    manager.openFile(file, FileEditorOpenOptions(requestFocus = true))

    assertSame(project, windowManager.getActiveProject(project), "The frame shows the project of the tab that takes the focus")
  }

  @Test
  fun `an open without the focus leaves the active project alone`(): Unit = timeoutRunBlocking {
    val file = createFile("background.txt")

    manager.openFile(file, FileEditorOpenOptions(requestFocus = false))

    assertSame(guest, windowManager.getActiveProject(project), "A background open shows no other project")
  }

  private fun createFile(name: String): VirtualFile {
    val path = tempDirFixture.get().resolve(name)
    path.writeText("content of $name")
    return checkNotNull(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path)) { "Can't find $path" }
  }
}
