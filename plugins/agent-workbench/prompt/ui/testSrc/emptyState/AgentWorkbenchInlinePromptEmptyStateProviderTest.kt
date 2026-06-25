// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui.emptyState

import com.intellij.agent.workbench.prompt.ui.AGENT_PROMPT_PALETTE_PREFERRED_SIZE
import com.intellij.agent.workbench.prompt.ui.AgentPromptBundle
import com.intellij.agent.workbench.prompt.ui.AgentPromptTextField
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.ui.components.JBTabbedPane
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.awt.Component
import java.awt.Container
import java.util.concurrent.TimeUnit

@TestApplication
@Timeout(value = 2, unit = TimeUnit.MINUTES)
class AgentWorkbenchInlinePromptEmptyStateProviderTest {
  @Test
  fun inlinePromptUsesCompactStructureAndAccessiblePromptText() {
    runInEdtAndWait {
      val component = AgentWorkbenchInlinePromptEmptyStateComponent(ProjectManager.getInstance().defaultProject)
      try {
        component.initialize()
        component.addNotify()

        val promptArea = collectComponents(component, AgentPromptTextField::class.java).single()
        val tabbedPane = collectComponents(component, JBTabbedPane::class.java).single()

        assertThat(component.name).isEqualTo(INLINE_PROMPT_COMPONENT_NAME)
        assertThat(component.preferredSize.height).isLessThan(AGENT_PROMPT_PALETTE_PREFERRED_SIZE.height)
        assertThat(tabbedPane.isVisible).isFalse()
        assertThat(promptArea.editor?.contentComponent?.accessibleContext?.accessibleName)
          .isEqualTo(AgentPromptBundle.message("inline.empty.state.prompt.accessible.name"))
        assertThat(promptArea.editor?.contentComponent?.accessibleContext?.accessibleDescription)
          .isEqualTo(AgentPromptBundle.message("inline.empty.state.prompt.accessible.description"))
      }
      finally {
        Disposer.dispose(component)
      }
    }
  }

  private fun <T : Component> collectComponents(component: Component, componentClass: Class<T>): List<T> {
    return buildList {
      if (componentClass.isInstance(component)) {
        add(componentClass.cast(component))
      }
      if (component is Container) {
        component.components.forEach { child -> addAll(collectComponents(child, componentClass)) }
      }
    }
  }
}
