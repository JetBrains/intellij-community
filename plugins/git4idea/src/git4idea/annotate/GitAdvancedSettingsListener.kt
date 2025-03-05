// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.annotate

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.advanced.AdvancedSettingsChangeListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import git4idea.util.GitFileUtils
import kotlinx.coroutines.CoroutineScope

object GitAdvancedSettingsListener {
  @JvmStatic
  fun registerListener(project: Project, activeScope: CoroutineScope) {
    val appBusConnection = ApplicationManager.getApplication().getMessageBus().connect(activeScope)
    appBusConnection.subscribe(AdvancedSettingsChangeListener.TOPIC, SettingsListener(project))
  }

  private class SettingsListener(val project: Project) : AdvancedSettingsChangeListener {
    override fun advancedSettingChanged(id: String, oldValue: Any, newValue: Any) {
      if (id == GitFileUtils.READ_CONTENT_WITH) {
        ProjectLevelVcsManager.getInstance(project).getContentRevisionCache().clearConstantCache()
      }
    }
  }
}
