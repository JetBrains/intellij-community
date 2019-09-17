/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package git4idea.branch

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.layout.*
import git4idea.repo.GitRepository
import git4idea.validators.validateName
import java.awt.event.KeyEvent

data class GitNewBranchOptions(val name: String, @get:JvmName("shouldCheckout") val checkout: Boolean)

internal class GitNewBranchDialog(project: Project,
                                  private val repositories: Collection<GitRepository>,
                                  dialogTitle: String,
                                  initialName: String?,
                                  private val showCheckOutOption: Boolean) : DialogWrapper(project, true) {
  private var checkout = true
  private var branchName = initialName.orEmpty()

  init {
    title = dialogTitle
    init()
  }

  fun showAndGetOptions() = if (showAndGet()) GitNewBranchOptions(branchName.trim(), checkout) else null

  override fun createCenterPanel() = panel {
    row {
      label("New branch name:")
    }
    row {
      textField(::branchName, { branchName = it }).focused().withValidationOnInput {
        validateName(repositories, it.text.orEmpty())?.forComponent(it)
      }
    }
    if (showCheckOutOption) {
      row {
        checkBox("Checkout branch", ::checkout).component.apply {
          mnemonic = KeyEvent.VK_C
        }
      }
    }

  }
}
