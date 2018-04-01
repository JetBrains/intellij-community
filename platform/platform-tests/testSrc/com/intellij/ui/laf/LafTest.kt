// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.laf

import com.intellij.openapi.application.invokeAndWaitIfNeed
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.UsefulTestCase
import com.intellij.ui.*
import com.intellij.ui.components.CheckBox
import com.intellij.ui.components.textFieldWithHistoryWithBrowseButton
import com.intellij.ui.layout.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File
import java.nio.file.Paths
import javax.swing.*
import kotlin.properties.Delegates

/**
 * Nor UI DSL, neither ComponentPanelBuilder should be used to create test panels.
 * To reduce possible side-effects and make LaF tests pure.
 */
@RunWith(Parameterized::class)
class LafTest {
  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun lafNames() = listOf("IntelliJ", "Darcula")
  }

  @Suppress("MemberVisibilityCanBePrivate")
  @Parameterized.Parameter
  lateinit var lafName: String

  @Rule
  @JvmField
  val testName = TestName()

  @Rule
  @JvmField
  val frameRule = FrameRule()

  @Before
  fun beforeMethod() {
    assumeTrue(!UsefulTestCase.IS_UNDER_TEAMCITY)

    changeLafIfNeed(lafName)
  }

  @Test
  fun components() {
    doTest {
      val spacing = createIntelliJSpacingConfiguration()
      val panel = JPanel(VerticalFlowLayout(VerticalFlowLayout.TOP, spacing.horizontalGap, spacing.verticalGap, true, false))
      panel.add(JTextField("text"))
      panel.add(JPasswordField("secret"))
      panel.add(JComboBox<String>(arrayOf("one", "two")))

      val field = JComboBox<String>(arrayOf("one", "two"))
      field.isEditable = true
      panel.add(field)

      panel.add(JButton("label"))
      panel.add(CheckBox("enabled"))
      panel.add(JRadioButton("label"))
      panel.add(JBIntSpinner(0, 0, 7))
      panel.add(textFieldWithHistoryWithBrowseButton(null, "File", FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor()))

      panel
    }
  }

  private fun doTest(panelCreator: () -> JPanel) {
    var panel: JPanel by Delegates.notNull()
    invokeAndWaitIfNeed {
      panel = panelCreator()
      frameRule.show(panel, minSize = null)
    }

    val snapshotName = testName.snapshotFileName
    validateUsingImage(frameRule.frame, "laf${File.separatorChar}${getSnapshotRelativePath(lafName, isForImage = true)}${File.separatorChar}$snapshotName")
    validateBounds(panel, Paths.get(PlatformTestUtil.getPlatformTestDataPath(), "ui", "laf", getSnapshotRelativePath(lafName, isForImage = false)), snapshotName)
  }
}