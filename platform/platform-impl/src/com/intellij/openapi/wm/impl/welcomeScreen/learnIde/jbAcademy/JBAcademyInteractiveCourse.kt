// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.welcomeScreen.learnIde.jbAcademy

import com.intellij.icons.AllIcons
import com.intellij.ide.plugins.PluginManager
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.wm.InteractiveCourseData
import com.intellij.openapi.wm.InteractiveCourseFactory
import com.intellij.util.PlatformUtils
import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.Action
import javax.swing.Icon
import javax.swing.JComponent


private val EDU_TOOLS_PLUGIN_ID = PluginId.getId("com.jetbrains.edu")
internal class JBAcademyInteractiveCourse : InteractiveCourseFactory {
  override val isActive: Boolean
    get() = PlatformUtils.isIntelliJ() ||
            PlatformUtils.isPyCharm() && !PlatformUtils.isDataSpell() ||
            PlatformUtils.isWebStorm() ||
            PlatformUtils.isCLion() ||
            PlatformUtils.isGoIde() ||
            PlatformUtils.isPhpStorm() ||
            PlatformUtils.isRustRover() ||
            PlatformUtils.getPlatformPrefix() == "AndroidStudio"

  override val isEnabled: Boolean = true

  override val disabledText: String = ""

  override fun getInteractiveCourseComponent(): JComponent = JBAcademyInteractiveCoursePanel(EduToolsInteractiveCourseData())
  override fun getCourseData(): InteractiveCourseData {
    return EduToolsInteractiveCourseData()
  }
}

private class EduToolsInteractiveCourseData : InteractiveCourseData {
  override fun getName(): String {
    return JBAcademyWelcomeScreenBundle.message("welcome.tab.jetbrains.academy.name")
  }

  override fun getDescription(): String {
    return JBAcademyWelcomeScreenBundle.message("welcome.tab.jetbrains.academy.description")
  }

  override fun getIcon(): Icon {
    return AllIcons.Welcome.LearnTab.JetBrainsAcademy
  }

  override fun getActionButtonName(): String {
    return if (PluginManager.isPluginInstalled(EDU_TOOLS_PLUGIN_ID) && !PluginManagerCore.isDisabled(EDU_TOOLS_PLUGIN_ID)) {
      JBAcademyWelcomeScreenBundle.message("welcome.tab.jetbrains.academy.get.started.button")
    }
    else {
      JBAcademyWelcomeScreenBundle.message("welcome.tab.jetbrains.academy.button.enable")
    }
  }

  override fun getAction(): Action {
    // dummy action that is never used for edutools course page
    return object : AbstractAction() {
      override fun actionPerformed(e: ActionEvent?) {
      }
    }
  }

  override fun isEduTools(): Boolean = true
}

