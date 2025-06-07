// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package training.learn

import com.intellij.application.options.CodeStyle
import com.intellij.ide.util.TipAndTrickManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.Messages
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.util.concurrency.annotations.RequiresEdt
import training.lang.LangSupport
import training.learn.exceptons.NoSdkException
import training.project.ProjectUtils

object NewLearnProjectUtil {
  private val LOG = logger<NewLearnProjectUtil>()

  @RequiresEdt
  fun createLearnProject(projectToClose: Project?,
                         langSupport: LangSupport,
                         selectedSdk: Sdk?,
                         postInitCallback: (learnProject: Project) -> Unit) {
    if (langSupport.useUserProjects) {
      error("Language support for ${langSupport.primaryLanguage} cannot create learning project.")
    }
    val unitTestMode = ApplicationManager.getApplication().isUnitTestMode

    ProjectUtils.importOrOpenProject(langSupport, projectToClose) { newProject ->
      TipAndTrickManager.DISABLE_TIPS_FOR_PROJECT.set(newProject, true)
      CodeStyle.setMainProjectSettings(newProject, CodeStyleSettings.getDefaults())
      try {
        val sdkForProject = langSupport.getSdkForProject(newProject, selectedSdk)
        if (sdkForProject != null) {
          langSupport.applyProjectSdk(sdkForProject, newProject)
        }
      }
      catch (e: NoSdkException) {
        LOG.error(e)
      }

      if (!unitTestMode) {
        newProject.save()
      }

      newProject.save()
      postInitCallback(newProject)
    }
  }

  fun showDialogOpenLearnProject(project: Project): Boolean {
    return Messages.showOkCancelDialog(project,
                                       LearnBundle.message("dialog.learnProjectWarning.message",
                                                           ApplicationNamesInfo.getInstance().fullProductName),
                                       LearnBundle.message("dialog.learnProjectWarning.title"),
                                       LearnBundle.message("dialog.learnProjectWarning.ok"),
                                       Messages.getCancelButton(),
                                       null) == Messages.OK
  }
}
