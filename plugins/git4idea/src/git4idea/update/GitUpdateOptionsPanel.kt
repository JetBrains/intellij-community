/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package git4idea.update

import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.layout.*
import com.intellij.util.ui.JBUI
import git4idea.config.GitVcsSettings

internal class GitUpdateOptionsPanel(private val settings: GitVcsSettings) {
  val panel = createPanel()

  private fun createPanel(): DialogPanel = panel {
    row {
      updateMethodButtonGroup(
        get = { settings.updateMethod == it },
        set = { selected, method -> if (selected) settings.updateMethod = method }
      )
    }
  }.withBorder(JBUI.Borders.empty(16, 5, 0, 5))

  fun isModified(): Boolean = panel.isModified()

  fun applyTo() = panel.apply()

  fun updateFrom() = panel.reset()
}