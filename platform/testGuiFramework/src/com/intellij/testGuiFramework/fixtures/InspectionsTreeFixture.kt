// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.fixtures

import com.intellij.profile.codeInspection.ui.inspectionsTree.InspectionsConfigTreeTable
import com.intellij.testGuiFramework.driver.ExtendedJTreeDriver
import com.intellij.testGuiFramework.fixtures.extended.ExtendedTreeFixture
import org.fest.swing.core.MouseButton
import org.fest.swing.core.Robot

class InspectionsTreeFixture(val robot: Robot, val target: InspectionsConfigTreeTable) :
  ComponentFixture<InspectionsTreeFixture, InspectionsConfigTreeTable>(InspectionsTreeFixture::class.java, robot, target) {

  @Suppress("unused")
  fun selectPath(vararg path: String) {
    val treeFixture = ExtendedTreeFixture(robot, target.tree)
    treeFixture.replaceDriverWith(ExtendedJTreeDriver(robot))
    treeFixture.clickPath(path.toList(), MouseButton.LEFT_BUTTON)
  }
}