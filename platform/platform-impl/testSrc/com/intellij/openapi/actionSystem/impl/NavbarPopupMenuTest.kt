// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem.impl

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

@TestApplication
internal class NavbarPopupMenuTest {
  @Test
  fun navbarPopupMenuIsNotSearchable() {
    // We keep NavbarPopupMenu action group in the platform because different plugins from different places contribute items here.
    // When NavBar plugin is installed - it brings its own searchable NavbarPopupMenuSearchable action group to preserve existing behavior.
    // However, I don't think this NavbarPopupMenuSearchable group is actually useful (to start with - its name is not unique), thus
    // I don't add test for NavbarPopupMenuSearchable because I don't mind if someone drops that group on purpose or by accident.
    //
    // This test is to make sure that "Navigation bar" is not searchable before Navigation bar plugin is installed,
    // and there are no two identical "Navigation bar" groups after.
    val popupMenu = ActionManager.getInstance().getAction(IdeActions.GROUP_NAVBAR_POPUP) as ActionGroup
    assertFalse(popupMenu.isSearchable)
  }
}
