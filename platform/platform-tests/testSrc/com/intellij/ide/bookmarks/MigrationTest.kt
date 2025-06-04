// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.bookmarks

import com.intellij.ide.bookmark.BookmarksManagerImpl
import com.intellij.openapi.editor.impl.AbstractEditorTest
import com.intellij.testFramework.PlatformTestUtil.waitForAlarm
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

class MigrationTest : AbstractEditorTest() {

  fun testNoStateLoaded() {
    val manager = BookmarksManagerImpl(project, CoroutineScope(SupervisorJob()))
    assertEquals(0, manager.bookmarks.size)
    assertEquals(0, manager.groups.size)
    assertNull(manager.defaultGroup)

    initText("point me")
    BookmarkManager.getInstance(project).apply {
      val bookmark = addTextBookmark(file.virtualFile, 0, "description")
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
