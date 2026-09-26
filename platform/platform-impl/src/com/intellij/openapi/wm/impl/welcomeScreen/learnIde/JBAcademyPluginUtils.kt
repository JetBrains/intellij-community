// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Internal

package com.intellij.openapi.wm.impl.welcomeScreen.learnIde

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.wm.impl.welcomeScreen.learnIde.coursesInProgress.CoursesInProgressPanel
import org.jetbrains.annotations.ApiStatus

fun getBrowseCoursesAction(): AnAction? {
  val browseCoursesActionId = "Educational.BrowseCourses"
  val action = ActionManager.getInstance().getAction(browseCoursesActionId)
  if (action == null) {
    logger<CoursesInProgressPanel>().warn("Cannot find BrowseCourses action by id: $browseCoursesActionId")
  }
  return action
}