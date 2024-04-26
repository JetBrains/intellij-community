// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.action

import com.intellij.codeInsight.AttachSourcesProvider
import com.intellij.codeInsight.AttachSourcesProvider.AttachSourcesAction
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.gradle.util.GradleConstants

class GradleAttachSourcesProvider : AttachSourcesProvider {

  override fun getActions(orderEntries: List<LibraryOrderEntry>, psiFile: PsiFile): Collection<AttachSourcesAction> {
    val gradleModules = getGradleModules(orderEntries)
    if (gradleModules.isEmpty()) {
      return emptyList()
    }
    val action = GradleDownloadSourceAction(orderEntries, psiFile) {
      getGradleModules(orderEntries)
    }
    return listOf(action)
  }

  private fun getGradleModules(libraryOrderEntries: List<LibraryOrderEntry>): Map<LibraryOrderEntry, Module> {
    val result = HashMap<LibraryOrderEntry, Module>()
    for (entry in libraryOrderEntries) {
      if (entry.isModuleLevel()) {
        continue
      }
      val module = entry.getOwnerModule()
      if (ExternalSystemApiUtil.isExternalSystemAwareModule(GradleConstants.SYSTEM_ID, module)) {
        result.put(entry, module)
      }
    }
    return result
  }
}