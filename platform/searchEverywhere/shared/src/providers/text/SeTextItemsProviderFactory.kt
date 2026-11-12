// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.providers.text

import com.intellij.ide.actions.GotoActionBase
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.readAction
import com.intellij.openapi.project.Project
import com.intellij.platform.searchEverywhere.SeItemsProvider
import com.intellij.platform.searchEverywhere.SeItemsProviderFactory
import com.intellij.platform.searchEverywhere.SeProviderIdUtils
import com.intellij.psi.SmartPointerManager


internal class SeTextItemsProviderFactory : SeItemsProviderFactory {
  override val id: String get() = SeProviderIdUtils.TEXT_ID

  override suspend fun getItemsProvider(project: Project?, dataContext: DataContext): SeItemsProvider? {
    if (project == null || !SeTextItemsProvider.isEnabled()) return null

    val psiContext = readAction {
      GotoActionBase.getPsiContext(dataContext)?.let { context ->
        SmartPointerManager.getInstance(project).createSmartPsiElementPointer(context)
      }
    }
    return SeTextItemsProvider(project, psiContext)
  }
}
