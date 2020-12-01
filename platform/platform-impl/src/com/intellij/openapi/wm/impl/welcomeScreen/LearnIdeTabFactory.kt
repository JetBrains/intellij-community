// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.welcomeScreen

import com.intellij.ide.IdeBundle
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.wm.WelcomeScreenTab
import com.intellij.openapi.wm.WelcomeTabFactory
import com.intellij.openapi.wm.impl.welcomeScreen.learnIde.LearnIdeContentPanel
import javax.swing.JComponent

class LearnIdeTabFactory: WelcomeTabFactory {
  override fun createWelcomeTab(parentDisposable: Disposable): WelcomeScreenTab {
    return object : TabbedWelcomeScreen.DefaultWelcomeScreenTab(IdeBundle.message("welcome.screen.learnIde.title", ApplicationNamesInfo.getInstance().fullProductName),
                                                                WelcomeScreenEventCollector.TabType.TabNavTutorials) {
      override fun buildComponent(): JComponent {
        return LearnIdeContentPanel(parentDisposable)
      }
    }
  }
}