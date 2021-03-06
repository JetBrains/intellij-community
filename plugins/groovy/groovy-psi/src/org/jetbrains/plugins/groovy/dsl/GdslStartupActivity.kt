// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.dsl

import com.intellij.ide.impl.getTrustedState
import com.intellij.ide.impl.setTrusted
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.intellij.psi.PsiManager
import com.intellij.util.ThreeState
import org.jetbrains.plugins.groovy.GroovyBundle

class GdslStartupActivity : StartupActivity.Background {

  override fun runActivity(project: Project) {
    if (project.getTrustedState() == ThreeState.UNSURE && hasUserGdsl(project)) {
      notifyGdslInUntrustedProject(project)
    }
  }

  private fun hasUserGdsl(project: Project): Boolean {
    return runReadAction {
      GroovyDslFileIndex.getProjectGdslFiles(project).isNotEmpty()
    }
  }

  private fun notifyGdslInUntrustedProject(project: Project) {
    val trustAction = NotificationAction.createSimpleExpiring(GroovyBundle.message("gdsl.trusted.project.answer.trust")) {
      project.setTrusted(true)
      PsiManager.getInstance(project).dropPsiCaches()
    }
    val dontTrustAction = NotificationAction.createSimpleExpiring(GroovyBundle.message("gdsl.trusted.project.answer.dont.trust")) {
      project.setTrusted(false)
    }
    DslErrorReporter.NOTIFICATION_GROUP
      .createNotification(
        GroovyBundle.message("gdsl.trusted.project.message"),
        NotificationType.INFORMATION
      )
      .addAction(trustAction)
      .addAction(dontTrustAction)
      .notify(project)
  }
}
