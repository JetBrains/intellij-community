// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.fixtures

import com.intellij.ui.components.JBTabbedPane
import org.fest.swing.core.Robot

class JBTabbedPaneFixture(private val title: String,
                          private val jbTabbedPane: JBTabbedPane,
                          robot: Robot) : ComponentFixture<JBTabbedPaneFixture, JBTabbedPane>(
  JBTabbedPaneFixture::class.java, robot, jbTabbedPane) {

  fun selectTab() {
    val tabIndex = jbTabbedPane.indexOfTab(title)
    val tab = jbTabbedPane.getTabComponentAt(tabIndex)
    robot().click(tab)
  }
}
