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
import com.intellij.ui.dsl.builder.bind
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import git4idea.config.GitVcsSettings
import git4idea.config.UpdateMethod

internal class GitUpdateOptionsPanel(private val settings: GitVcsSettings) {
  val panel = createPanel()

  private fun createPanel(): DialogPanel = panel {
    buttonsGroup {
      getUpdateMethods().forEach { method ->
        row {
          radioButton(method.presentation, method)
        }
      }
    }.bind({ settings.updateMethod }, { settings.updateMethod = it })
  }.withBorder(JBUI.Borders.empty(8, 8, 2, 8))

  fun isModified(): Boolean = panel.isModified()

  fun applyTo() = panel.apply()

  fun updateFrom() = panel.reset()
}

internal fun getUpdateMethods(): List<UpdateMethod> = listOf(UpdateMethod.MERGE, UpdateMethod.REBASE)
