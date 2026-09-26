// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ChangeListManagerStatusProvider
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.ui.components.panels.VerticalLayout.FILL
import com.intellij.ui.components.panels.Wrapper
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.launchOnShow
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent

@ApiStatus.Internal
object ChangesViewUIUtil {
  /**
   * A gap between status components in the changes view.
   */
  private const val CHANGES_VIEW_STATUSES_GAP: Int = 2

  /**
   * A panel showing changes manager status
   */
  fun createStatusPanel(project: Project): JComponent {
    val wrapper = Wrapper().apply {
      minimumSize = JBUI.emptySize()
    }

    wrapper.launchOnShow("Changes view status panel") {
      fun update(components: List<JComponent>) {
        val content = if (components.isEmpty()) {
          null
        }
        else {
          JBPanel<JBPanel<*>?>(VerticalLayout(CHANGES_VIEW_STATUSES_GAP, FILL)).apply {
            for (component in components) {
              add(component)
            }
          }
        }
        wrapper.setContent(content)
      }

      try {
        ChangeListManagerStatusProvider.consumeStatusComponents(project) {
          update(it)
        }
      }
      finally {
        update(emptyList())
      }
    }
    return wrapper
  }
}