// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.bookmarks

import com.intellij.ide.bookmark.BookmarksManagerImpl
import com.intellij.openapi.editor.impl.editorModelFixture
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.testFramework.PlatformTestUtil.waitForAlarm
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

@TestApplication
class MigrationTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val fixture by editorModelFixture(codeInsightModuleFixture(projectFixture))

  @Test
  fun testNoStateLoaded(): Unit = fixture.runTest {
    val manager = BookmarksManagerImpl(projectFixture.get(), CoroutineScope(SupervisorJob()))
    assertEquals(0, manager.bookmarks.size)
    assertEquals(0, manager.groups.size)
    assertNull(manager.defaultGroup)

    initText("point me")
    BookmarkManager.getInstance(projectFixture.get())!!.apply {
      val bookmark = addTextBookmark(fixture.file.virtualFile, 0, "description")
      manager.noStateLoaded()
      waitForAlarm(50)
      removeBookmark(bookmark)
    }

    assertEquals(1, manager.bookmarks.size)
    assertEquals(1, manager.groups.size)
    assertNull(manager.defaultGroup)

    val group = manager.groups[0]
    val bookmark = manager.bookmarks[0]
    assertEquals("description", group.getDescription(bookmark))
  }
}
