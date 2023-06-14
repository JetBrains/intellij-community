// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.welcomeScreen.learnIde

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction

fun getBrowseCoursesAction(): AnAction? {
  return ActionManager.getInstance().getAction("Educational.BrowseCourses")
}