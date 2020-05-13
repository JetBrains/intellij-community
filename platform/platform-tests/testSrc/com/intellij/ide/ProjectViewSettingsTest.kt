// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide

import com.intellij.ide.projectView.ProjectViewSettings
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.util.treeView.NodeOptions
import org.junit.Assert
import org.junit.Test

class ProjectViewSettingsTest {

  @Test
  fun testDefaultNodeOptions() {
    assertDefaultNodeOptions(NodeOptions.Immutable.DEFAULT)
    assertDefaultNodeOptions(object : NodeOptions {})
  }

  private fun assertDefaultNodeOptions(options: NodeOptions) {
    Assert.assertFalse(options.isFlattenPackages)
    Assert.assertFalse(options.isAbbreviatePackageNames)
    Assert.assertFalse(options.isHideEmptyMiddlePackages)
    Assert.assertFalse(options.isCompactDirectories)
    Assert.assertFalse(options.isShowLibraryContents)
  }


  @Test
  fun testDefaultViewSettings() {
    assertDefaultViewSettings(ViewSettings.Immutable.DEFAULT)
    assertDefaultViewSettings(object : ViewSettings {})
  }

  private fun assertDefaultViewSettings(settings: ViewSettings) {
    assertDefaultNodeOptions(settings)
    Assert.assertTrue(settings.isFoldersAlwaysOnTop)
    Assert.assertFalse(settings.isShowMembers)
    Assert.assertFalse(settings.isStructureView)
    Assert.assertTrue(settings.isShowModules)
    Assert.assertFalse(settings.isFlattenModules)
    Assert.assertTrue(settings.isShowURL)
  }


  @Test
  fun testDefaultProjectViewSettings() {
    assertDefaultProjectViewSettings(ProjectViewSettings.Immutable.DEFAULT)
    assertDefaultProjectViewSettings(object : ProjectViewSettings {})
  }

  private fun assertDefaultProjectViewSettings(settings: ProjectViewSettings) {
    assertDefaultViewSettings(settings)
    Assert.assertTrue(settings.isShowExcludedFiles)
    Assert.assertFalse(settings.isShowVisibilityIcons)
    Assert.assertTrue(settings.isUseFileNestingRules)
  }
}