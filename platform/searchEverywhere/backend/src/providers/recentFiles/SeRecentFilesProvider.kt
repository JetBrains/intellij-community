// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.backend.providers.recentFiles

import com.intellij.ide.IdeBundle
import com.intellij.ide.util.gotoByName.ChooseByNameItemProvider
import com.intellij.ide.util.gotoByName.FileTypeRef
import com.intellij.ide.util.gotoByName.GotoFileModel
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.fileEditor.impl.EditorHistoryManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.platform.searchEverywhere.SeExtendedInfoProvider
import com.intellij.platform.searchEverywhere.SeItem
import com.intellij.platform.searchEverywhere.SeItemsPreviewProvider
import com.intellij.platform.searchEverywhere.SeItemsProvider
import com.intellij.platform.searchEverywhere.SeParams
import com.intellij.platform.searchEverywhere.SePreviewInfo
import com.intellij.platform.searchEverywhere.SeProviderIdUtils
import com.intellij.platform.searchEverywhere.providers.target.SeTargetItemsProvider
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.Nls

internal class SeRecentFilesProvider private constructor(
  private val targetProvider: SeTargetItemsProvider<FileTypeRef>,
) : SeItemsProvider,
    SeItemsPreviewProvider,
    SeExtendedInfoProvider {
  override val id: String get() = SeProviderIdUtils.RECENT_FILES_ID
  override val displayName: @Nls String get() = IdeBundle.message("search.everywhere.group.name.recent.files")

  override suspend fun collectItems(params: SeParams, collector: SeItemsProvider.Collector): Unit =
    targetProvider.collectItems(params, collector)

  override suspend fun itemSelected(item: SeItem, modifiers: Int, searchText: String): Boolean =
    targetProvider.itemSelected(item, this, modifiers, searchText)

  override suspend fun performExtendedAction(item: SeItem): Boolean =
    targetProvider.performExtendedAction(item, this)

  override suspend fun getPreviewInfo(item: SeItem, project: Project): SePreviewInfo? =
    targetProvider.getPreviewInfo(item)

  /** True, because `RecentFilesSEContributor` inherits `showInFindResults` from the goto contributor. */
  override suspend fun canBeShownInFindResults(): Boolean = true

  override fun dispose() {
    // The target provider holds the disposables that a preview fetch opened.
    Disposer.dispose(targetProvider)
  }

  companion object {
    suspend fun create(project: Project, dataContext: DataContext): SeRecentFilesProvider {
      EditorHistoryManager.preloadHistory(project)

      val targetProvider = SeTargetItemsProvider.create<FileTypeRef>(
        project = project,
        dataContext = dataContext,
        operationDisposable = null,
        label = "SeRecentFiles",
        gotoModelProvider = { modelProject, _, _ ->
          object : GotoFileModel(modelProject) {
            override fun getItemProvider(context: PsiElement?): ChooseByNameItemProvider {
              return SeRecentFilesGotoItemProvider(project, context, this)
            }
          }
        },
        acceptsBlankQuery = true,
        supportsScopes = false,
      )

      return SeRecentFilesProvider(targetProvider)
    }
  }
}
