// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.action

import com.intellij.codeInsight.AttachSourcesProvider
import com.intellij.codeInsight.AttachSourcesProvider.AttachSourcesAction
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.util.ActionCallback
import com.intellij.psi.PsiFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.plugins.gradle.service.sources.GradleLibrarySourcesDownloader
import org.jetbrains.plugins.gradle.util.GradleBundle

class GradleAttachSourcesProvider(private val cs: CoroutineScope) : AttachSourcesProvider {

  override fun getActions(orderEntries: List<LibraryOrderEntry>, psiFile: PsiFile): Collection<AttachSourcesAction> {
    if (!GradleLibrarySourcesDownloader.canDownloadSources(orderEntries)) {
      return emptyList()
    }
    val action = object : AttachSourcesAction {
      override fun getName(): String = GradleBundle.message("gradle.action.download.sources")

      override fun getBusyText(): String = GradleBundle.message("gradle.action.download.sources.busy.text")

      override fun perform(orderEntires: List<LibraryOrderEntry>): ActionCallback {
        val actionCallback = ActionCallback()
        cs.launch {
          val path = GradleLibrarySourcesDownloader.download(psiFile.project, orderEntires)
          if (path != null) {
            actionCallback.setDone()
          }
          else {
            actionCallback.setRejected()
          }
        }
        return actionCallback
      }
    }
    return listOf(action)
  }
}