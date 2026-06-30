// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui

import com.intellij.agent.workbench.prompt.core.AgentPromptContextItem
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.runInEdtAndWait
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit
import javax.swing.JButton

@TestApplication
@Timeout(value = 2, unit = TimeUnit.MINUTES)
class AgentPromptContextChipsComponentTest {
  @Test
  fun contextChipRemoveButtonRestoresPromptFocusAfterRemoval() {
    runInEdtAndWait {
      val events = ArrayList<String>()
      val entry = ContextEntry(
        item = AgentPromptContextItem(
          rendererId = "test",
          title = "File",
          body = "src/Main.kt",
        ),
      )
      val contextChips = AgentPromptContextChipsComponent(
        onRemoveCompleted = { events += "focus" },
      ) { removedEntry ->
        assertThat(removedEntry).isSameAs(entry)
        events += "remove"
      }
      contextChips.render(listOf(entry))

      val removeButton = collectComponentsOfType(contextChips.component, JButton::class.java).single { button ->
        button.getClientProperty(CONTEXT_ATTACHMENT_REMOVE_PROPERTY) == true
      }
      removeButton.doClick()

      assertThat(events).containsExactly("remove", "focus")
    }
  }
}
