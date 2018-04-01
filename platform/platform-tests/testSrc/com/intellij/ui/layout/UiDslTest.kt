// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.layout

import com.intellij.openapi.application.invokeAndWaitIfNeed
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.UsefulTestCase
import com.intellij.ui.*
import net.miginfocom.layout.LayoutUtil
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File
import java.nio.file.Paths
import javax.swing.JPanel
import kotlin.properties.Delegates

/**
 * Set `test.update.snapshots=true` to automatically update snapshots if need.
 *
 * Checkout git@github.com:develar/intellij-ui-dsl-test-snapshots.git (or create own repo) to some local dir and set env LAYOUT_IMAGE_REPO
 * to use image snapshots.
 */
@RunWith(Parameterized::class)
class UiDslTest {
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
  fun `align fields in the nested grid`() {
    doTest { alignFieldsInTheNestedGrid() }
  }

  @Test
  fun `align fields`() {
    doTest { labelRowShouldNotGrow() }
  }

  @Test
  fun cell() {
    doTest { cellPanel() }
  }

  @Test
  fun `note row in the dialog`() {
    doTest { noteRowInTheDialog() }
  }

  @Test
  fun `visual paddings`() {
    doTest { visualPaddingsPanel() }
  }

  private fun doTest(panelCreator: () -> JPanel) {
    var panel: JPanel by Delegates.notNull()
    invokeAndWaitIfNeed {
      // otherwise rectangles are not set
      LayoutUtil.setGlobalDebugMillis(1000)

      panel = panelCreator()
      frameRule.show(panel)
    }

    val snapshotName = testName.snapshotFileName
    validateUsingImage(frameRule.frame, "layout${File.separatorChar}${getSnapshotRelativePath(lafName, isForImage = true)}${File.separatorChar}$snapshotName")
    validateBounds(panel, Paths.get(PlatformTestUtil.getPlatformTestDataPath(), "ui", "layout", getSnapshotRelativePath(lafName, isForImage = false)), snapshotName)
  }
}