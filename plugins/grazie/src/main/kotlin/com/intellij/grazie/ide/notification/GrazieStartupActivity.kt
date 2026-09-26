// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.ide.notification

import com.intellij.grazie.GrazieConfig
import com.intellij.grazie.remote.GrazieRemote
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

internal class GrazieNotificationComponent : ProjectActivity {
  override suspend fun execute(project: Project) {
    val state = GrazieConfig.get()
    if (!state.hasMissedLanguages()) return

    if (state.autoUpdateLanguages) {
      GrazieRemote.downloadMissing(project)
    } else {
      GrazieToastNotifications.showMissedLanguages(project)
    }
  }
}
