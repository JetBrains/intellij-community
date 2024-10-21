// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.shelf

import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManagerListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private class ShelveChangeManagerProjectActivity : ProjectActivity {
  override suspend fun execute(project: Project) {
    val manager = project.serviceAsync<ShelveChangesManager>()
    blockingContext {
      manager.projectOpened()
    }
  }
}

private class ShelvedChangesViewManagerShelfManagerListener(private val project: Project) : ShelveChangesManagerListener {
  override fun shelvedListsChanged() {
    ShelveChangesManager.getInstance(project).coroutineScope.launch(Dispatchers.EDT) {
      project.messageBus.syncPublisher(ChangesViewContentManagerListener.TOPIC).toolWindowMappingChanged()
    }
  }
}