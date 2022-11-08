// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package training.ui.welcomeScreen

import com.intellij.ide.plugins.PluginManager
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.wm.InteractiveCourseData
import com.intellij.openapi.wm.InteractiveCourseFactory
import com.intellij.util.PlatformUtils
import training.FeaturesTrainerIcons
import training.learn.LearnBundle
import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.Action
import javax.swing.Icon


private val EDU_TOOLS_PLUGIN_ID = PluginId.getId("com.jetbrains.edu")
internal class EduToolsInteractiveCourse : InteractiveCourseFactory {
  override fun getInteractiveCourseData(): InteractiveCourseData? {
    return if (PlatformUtils.isIntelliJ() ||
               PlatformUtils.isPyCharm() && !PlatformUtils.isDataSpell() ||
               PlatformUtils.isGoIde()) {
      EduToolsInteractiveCourseData()
    }
    else {
      null
    }
  }
}

private class EduToolsInteractiveCourseData : InteractiveCourseData {
  override fun getName(): String {
    return LearnBundle.message("welcome.tab.edutools.name")
  }

  override fun getDescription(): String {
    return LearnBundle.message("welcome.tab.edutools.description")
  }

  override fun getIcon(): Icon {
    return FeaturesTrainerIcons.EduTools
  }

  override fun getActionButtonName(): String {
    return if (PluginManager.isPluginInstalled(EDU_TOOLS_PLUGIN_ID) && !PluginManagerCore.isDisabled(EDU_TOOLS_PLUGIN_ID)) {
      LearnBundle.message("welcome.tab.edutools.get.started.button")
    }
    else {
      LearnBundle.message("welcome.tab.edutools.button.enable")
    }
  }

  override fun getAction(): Action {
    // dummy action that is never used for edutools course page
    return object : AbstractAction() {
      override fun actionPerformed(e: ActionEvent?) {
      }
    }
  }

  override fun isEduTools(): Boolean {
    return true
  }
}

