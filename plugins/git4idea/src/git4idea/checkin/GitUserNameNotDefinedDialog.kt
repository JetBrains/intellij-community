/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package git4idea.checkin

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.dsl.builder.*
import com.intellij.util.SystemProperties
import com.intellij.vcs.log.VcsUser
import git4idea.config.GitVcsSettings
import git4idea.i18n.GitBundle
import javax.swing.JComponent

internal class GitUserNameNotDefinedDialog(
  project: Project,
  private val rootsWithUndefinedProps: Collection<VirtualFile>,
  private val allRootsAffectedByCommit: Collection<VirtualFile>,
  private val rootsWithDefinedProps: Map<VirtualFile, VcsUser>
) : DialogWrapper(project, false) {
  private val settings = GitVcsSettings.getInstance(project)

  var userName: String = ""
    private set
  var userEmail: String = ""
    private set
  val isSetGlobalConfig: Boolean get() = settings.shouldSetUserNameGlobally()

  init {
    title = GitBundle.message("title.user.name.email.not.specified")
    setOKButtonText(GitBundle.message("button.set.name.and.commit"))

    init()
  }

  private fun calcProposedUser(rootsWithDefinedProps: Map<VirtualFile, VcsUser>): VcsUser? {
    if (rootsWithDefinedProps.isEmpty()) {
      return null
    }
    val iterator = rootsWithDefinedProps.entries.iterator()
    val firstValue = iterator.next().value
    while (iterator.hasNext()) {
      // nothing to propose if there are different values set in different repositories
      if (firstValue != iterator.next().value) {
        return null
      }
    }
    return firstValue
  }

  override fun createCenterPanel(): JComponent {
    val message = getMessageText()

    val proposedUser = calcProposedUser(rootsWithDefinedProps)
    userName = proposedUser?.name ?: SystemProperties.getUserName()
    userEmail = proposedUser?.email.orEmpty()

    return panel {
      if (message != null)
        row {
          text(message)
        }
      row(GitBundle.message("label.user.name")) {
        textField()
          .columns(COLUMNS_MEDIUM)
          .focused()
          .bindText(::userName)
          .validationOnApply {
            when {
              it.text.isBlank() -> error(GitBundle.message("validation.warning.set.name.email.for.git"))
              else -> null
            }
          }
      }
      row(GitBundle.message("label.user.email")) {
        textField()
          .columns(COLUMNS_MEDIUM)
          .bindText(::userEmail)
          .validationOnApply {
            when {
              it.text.isBlank() -> error(GitBundle.message("validation.warning.set.name.email.for.git"))
              !it.text.contains("@") -> error(GitBundle.message("validation.error.email.no.at"))
              else -> null
            }
          }
      }
      row {
        checkBox(GitBundle.message("checkbox.set.config.property.globally"))
          .bindSelected({ settings.shouldSetUserNameGlobally() },
                        { settings.setUserNameGlobally(it) })
      }
    }
  }

  private fun getMessageText(): @NlsContexts.Label String? {
    if (allRootsAffectedByCommit.size == rootsWithUndefinedProps.size) {
      return null
    }
    val sb = HtmlBuilder()
      .append(GitBundle.message("label.name.email.not.defined.in.n.roots", rootsWithUndefinedProps.size))
    for (root in rootsWithUndefinedProps) {
      sb.br().append(root.presentableUrl)
    }
    return sb.toString()
  }
}