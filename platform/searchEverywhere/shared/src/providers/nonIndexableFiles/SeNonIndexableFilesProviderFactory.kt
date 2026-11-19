// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.providers.nonIndexableFiles

import com.intellij.ide.actions.GotoActionBase
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.readAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.ex.WelcomeScreenProjectProvider
import com.intellij.platform.searchEverywhere.SeItemsProvider
import com.intellij.platform.searchEverywhere.SeItemsProviderFactory
import com.intellij.platform.searchEverywhere.SeProviderIdUtils
import com.intellij.psi.SmartPointerManager

/**
 * Creates [SeNonIndexableFilesProvider] when the registry turns on the search of the non-indexable files.
 *
 * The welcome screen project gets no provider.
 */
internal class SeNonIndexableFilesProviderFactory : SeItemsProviderFactory {
  override val id: String
    get() = SeProviderIdUtils.NON_INDEXABLE_FILES_ID

  override suspend fun getItemsProvider(project: Project?, dataContext: DataContext): SeItemsProvider? {
    if (project == null
        || !Registry.`is`("se.enable.non.indexable.files.contributor")
        || WelcomeScreenProjectProvider.isWelcomeScreenProject(project)) return null

    val psiContext = readAction {
      GotoActionBase.getPsiContext(dataContext)?.let { context ->
        SmartPointerManager.getInstance(project).createSmartPsiElementPointer(context)
      }
    }
    return SeNonIndexableFilesProvider(project, psiContext)
  }
}
