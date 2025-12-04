// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.platform.searchEverywhere.SeSession
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus

/**
 * Factory for creating the Search Everywhere tabs.
 */
@ApiStatus.Experimental
interface SeTabFactory {
  val id: String

  /**
   * Creates a Search Everywhere tab based on the provided parameters.
   * see [com.intellij.platform.searchEverywhere.frontend.tabs.SeDefaultTabBase]
   *
   * @param scope the coroutine scope of the current Search Everywhere popup for asynchronous operations
   * @param project the current project, or null if no project is available
   * @param session the current search session
   * @param initEvent the initial action event causing the Search Everywhere popup to be opened
   * @param registerShortcut a function to register shortcuts for the tab
   * @return a SeTab instance or null if the tab cannot be created
   */
  suspend fun getTab(scope: CoroutineScope, project: Project?, session: SeSession, initEvent: AnActionEvent, registerShortcut: (AnAction) -> Unit): SeTab? = null

  companion object {
    @ApiStatus.Internal
    val EP_NAME: ExtensionPointName<SeTabFactory> = ExtensionPointName("com.intellij.searchEverywhere.tabFactory")
  }
}