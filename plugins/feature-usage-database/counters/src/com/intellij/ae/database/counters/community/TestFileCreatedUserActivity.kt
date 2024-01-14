// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ae.database.counters.community

import com.intellij.ae.database.core.activities.WritableDatabaseBackedCounterUserActivity
import com.intellij.ae.database.core.runUpdateEvent
import com.intellij.openapi.application.readAction
import com.intellij.openapi.fileTypes.PlainTextLanguage
import com.intellij.openapi.project.ProjectLocator
import com.intellij.openapi.roots.TestSourcesFilter
import com.intellij.openapi.vfs.AsyncFileListener
import com.intellij.openapi.vfs.AsyncFileListener.ChangeApplier
import com.intellij.openapi.vfs.newvfs.events.VFileCopyEvent
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.psi.impl.PsiManagerEx
import com.intellij.psi.util.validOrNull

object TestFileCreatedUserActivity : WritableDatabaseBackedCounterUserActivity() {
  override val id = "test.file.created"

  internal suspend fun write() {
    submit(1)
  }
}

internal class TestFileCreationListener : AsyncFileListener {
  override fun prepareChange(events: MutableList<out VFileEvent>): ChangeApplier {
    val filteredEvents = events.filter { it is VFileCreateEvent || it is VFileCopyEvent }

    return object : ChangeApplier {
      override fun afterVfsChange() {
        for (event in filteredEvents) {
          val file = event.file ?: return
          val project = ProjectLocator.getInstance().guessProjectForFile(file) ?: return
          val isTest = TestSourcesFilter.isTestSources(file, project)
          if (isTest) {
            FeatureUsageDatabaseCountersScopeProvider.getScope().runUpdateEvent(TestFileCreatedUserActivity) {
              val psiFile = readAction {
                PsiManagerEx.getInstance(project).findFile(file)?.validOrNull()
              }

              if (psiFile == null) {
                return@runUpdateEvent
              }

              // try to write only actual tests, not test data and etc
              if (psiFile.language != PlainTextLanguage.INSTANCE && !file.fileType.isBinary) {
                it.write()
              }
            }
          }
        }
      }
    }
  }
}