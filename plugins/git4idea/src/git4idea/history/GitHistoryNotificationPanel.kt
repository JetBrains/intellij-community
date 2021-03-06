// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.history

import com.intellij.icons.AllIcons
import com.intellij.ide.ui.RegistryBooleanOptionDescriptor
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.IconButton
import com.intellij.openapi.vcs.history.VcsHistorySession
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.InplaceButton
import com.intellij.ui.LightColors
import com.intellij.vcs.log.data.index.VcsLogBigRepositoriesList
import com.intellij.vcs.log.data.index.VcsLogModifiableIndex
import com.intellij.vcs.log.history.isNewHistoryEnabled
import com.intellij.vcs.log.impl.VcsLogSharedSettings
import com.intellij.vcs.log.impl.VcsProjectLog
import com.intellij.vcs.log.util.VcsLogUtil
import git4idea.i18n.GitBundle
import git4idea.log.GitLogProvider
import java.awt.BorderLayout

private const val INDEXING_NOTIFICATION_DISMISSED_KEY = "git.history.resume.index.dismissed"

object GitHistoryNotificationPanel {

  @JvmStatic
  fun create(project: Project, session: VcsHistorySession): EditorNotificationPanel? {
    val filePath = (session as? GitHistoryProvider.GitHistorySession)?.filePath ?: return null
    if (PropertiesComponent.getInstance(project).getBoolean(INDEXING_NOTIFICATION_DISMISSED_KEY)) return null
    if (!isNewHistoryEnabled()) return null
    if (!VcsLogSharedSettings.isIndexSwitchedOn(project)) return null

    val root = VcsLogUtil.getActualRoot(project, filePath) ?: return null
    if (!VcsLogBigRepositoriesList.getInstance().isBig(root) && GitLogProvider.isIndexingOn()) {
      return null
    }

    return EditorNotificationPanel(LightColors.YELLOW).apply {
      text = GitBundle.message("history.indexing.disabled.notification.text")
      createActionLabel(GitBundle.message("history.indexing.disabled.notification.resume.link")) {
        VcsLogBigRepositoriesList.getInstance().removeRepository(root)
        if (!GitLogProvider.isIndexingOn()) {
          GitLogProvider.getIndexingRegistryOption().setValue(true)
          RegistryBooleanOptionDescriptor.suggestRestartIfNecessary(this)
        }
        else {
          (VcsProjectLog.getInstance(project)?.dataManager?.index as? VcsLogModifiableIndex)?.scheduleIndex(false)
        }
        this.parent?.remove(this)
      }
      add(InplaceButton(IconButton(GitBundle.message("history.indexing.disabled.notification.dismiss.link"),
                                   AllIcons.Actions.Close, AllIcons.Actions.CloseHovered)) {
        PropertiesComponent.getInstance(project).setValue(INDEXING_NOTIFICATION_DISMISSED_KEY, true)
        this.parent?.remove(this)
      }, BorderLayout.EAST)
    }
  }
}