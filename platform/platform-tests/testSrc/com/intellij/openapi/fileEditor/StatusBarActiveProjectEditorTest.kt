// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor

import com.intellij.ide.impl.OpenProjectTask
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl
import com.intellij.openapi.fileEditor.impl.FileEditorOpenOptions
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ex.WindowManagerEx
import com.intellij.openapi.wm.impl.status.widget.StatusBarWidgetsManager
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.fileEditorManagerFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import kotlinx.coroutines.flow.first
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import kotlin.io.path.writeText

/**
 * The status bar of a frame with guests follows the active project of the frame: its current editor is the selected editor
 * of the project that the frame shows, see `StatusBarWidgetsManager.dataContext`.
 *
 * The projects are real, so each has its own `FileEditorManager`. The first one owns the frame, the second one is its guest,
 * and the third one is a guest that still opens.
 */
@TestApplication
@Timeout(30)
class StatusBarActiveProjectEditorTest {
  private val ownerFixture = projectFixture(openProjectTask = allowEditors(), openAfterCreation = true)
  private val guestFixture = projectFixture(openProjectTask = allowEditors(), openAfterCreation = true)
  /** Created but not opened, so not initialized: a guest that still opens. */
  private val openingGuestFixture = projectFixture(openProjectTask = allowEditors(), openAfterCreation = false)
  private val ownerEditorsFixture = ownerFixture.fileEditorManagerFixture()
  private val guestEditorsFixture = guestFixture.fileEditorManagerFixture()
  private val openingGuestEditorsFixture = openingGuestFixture.fileEditorManagerFixture()
  private val tempDirFixture = tempPathFixture()

  private val owner: Project
    get() = ownerFixture.get()

  private val guest: Project
    get() = guestFixture.get()

  private val windowManager: WindowManagerEx
    get() = WindowManagerEx.getInstanceEx()

  @BeforeEach
  fun joinGuest() {
    windowManager.addFrameGuest(owner, guest)
  }

  @AfterEach
  fun leaveGuest() {
    windowManager.removeFrameGuest(guest)
  }

  @Test
  fun `the status bar shows the editor of the active guest and of the owner again`(): Unit = timeoutRunBlocking {
    val ownerEditor = openEditor(ownerEditorsFixture.get(), createFile("owner.txt"))
    val guestEditor = openEditor(guestEditorsFixture.get(), createFile("guest.txt"))
    assertNotSame(ownerEditor, guestEditor)
    val currentEditor = owner.service<StatusBarWidgetsManager>().dataContext.currentFileEditor

    windowManager.setActiveProject(guest)
    currentEditor.first { it === guestEditor }

    windowManager.setActiveProject(owner)
    currentEditor.first { it === ownerEditor }
  }

  @Test
  fun `the status bar of a frame without an active guest shows the editor of the owner`(): Unit = timeoutRunBlocking {
    val ownerEditor = openEditor(ownerEditorsFixture.get(), createFile("alone.txt"))
    openEditor(guestEditorsFixture.get(), createFile("hidden.txt"))
    windowManager.setActiveProject(owner)

    owner.service<StatusBarWidgetsManager>().dataContext.currentFileEditor.first { it === ownerEditor }
  }

  @Test
  fun `a guest that turns active while it opens gives its editor once it has one`(): Unit = timeoutRunBlocking {
    val openingGuest = openingGuestFixture.get()
    assertFalse(openingGuest.isInitialized, "The guest still opens")
    openEditor(ownerEditorsFixture.get(), createFile("host.txt"))
    windowManager.addFrameGuest(owner, openingGuest)
    try {
      windowManager.setActiveProject(openingGuest)
      val currentEditor = owner.service<StatusBarWidgetsManager>().dataContext.currentFileEditor

      val guestEditor = openEditor(openingGuestEditorsFixture.get(), createFile("member.txt"))

      currentEditor.first { it === guestEditor }
    }
    finally {
      windowManager.removeFrameGuest(openingGuest)
    }
  }

  private suspend fun openEditor(manager: FileEditorManagerImpl, file: VirtualFile): FileEditor {
    // no focus: an open with the focus would make its project active, and the test sets the active project itself
    manager.openFile(file, FileEditorOpenOptions(requestFocus = false))
    return manager.selectedEditorFlow.first { it?.file == file }!!
  }

  private fun createFile(name: String): VirtualFile {
    val path = tempDirFixture.get().resolve(name)
    path.writeText("content of $name")
    return checkNotNull(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path)) { "Can't find $path" }
  }
}

private fun allowEditors(): OpenProjectTask = OpenProjectTask {
  beforeInitTasks += { it.putUserData(FileEditorManagerKeys.ALLOW_IN_LIGHT_PROJECT, true) }
}
