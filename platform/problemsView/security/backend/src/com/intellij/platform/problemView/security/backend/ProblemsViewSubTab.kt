// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.problemView.security.backend

import com.intellij.openapi.Disposable
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import javax.swing.Icon
import javax.swing.JComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls

@ApiStatus.Internal
data class ProblemsViewSubTabPresentation(
  @param:NlsContexts.TabTitle val title: String,
  val problemCount: Int? = null,
  @param:Nls val detail: String? = null,
  val icon: Icon? = null,
  val foundNoIssues: Boolean = false,
)

@ApiStatus.Internal
interface ProblemsViewSubTab {
  @get:NonNls
  val id: String

  val presentation: StateFlow<ProblemsViewSubTabPresentation>

  val component: JComponent

  @get:NonNls
  val gearActionGroupId: String?
    get() = null

  fun selectionChangedTo(selected: Boolean) {
  }
}

@ApiStatus.Internal
interface ProblemsViewSubTabProvider {
  companion object {
    @JvmStatic
    val EP: ExtensionPointName<ProblemsViewSubTabProvider> = ExtensionPointName("com.intellij.securityProblemsViewSubTab")
  }

  @get:NonNls
  val hostTabId: String

  /**
   * Returns `null` when this provider contributes no sub-tab to [project].
   *
   * @param scope the scope of the sub-tab. The host tab cancels it.
   * @param parentDisposable the parent of the sub-tab resources.
   */
  fun createSubTab(project: Project, scope: CoroutineScope, parentDisposable: Disposable): ProblemsViewSubTab?
}
