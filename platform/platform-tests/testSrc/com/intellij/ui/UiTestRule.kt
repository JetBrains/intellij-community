// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui

import com.intellij.openapi.util.IconLoader
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.util.SmartList
import com.intellij.util.ui.JBUI
import org.junit.ClassRule
import org.junit.rules.TestName
import org.junit.runners.model.MultipleFailureException
import java.nio.file.Path
import java.nio.file.Paths
import javax.swing.JPanel

class UiTestRule(private val testDataRoot: Path) : RequireHeadlessMode() {
  companion object {
    @JvmField
    @ClassRule
    val uiRule = UiTestRule(Paths.get(PlatformTestUtil.getPlatformTestDataPath(), "ui", "layout"))
  }

  override fun before() {
    super.before()

    IconManager.activate(null)
    IconLoader.activate()
  }

  override fun after() {
    super.after()

    IconManager.deactivate()
    IconLoader.deactivate()
  }

  fun validate(panel: JPanel, testName: TestName, lafName: String) {
    validate(panel, testName.snapshotFileName, lafName)
  }

  fun validate(panel: JPanel, snapshotName: String, lafName: String) {
    val snapshotDir = testDataRoot.resolve(getSnapshotRelativePath(lafName))
    val svgRenderer = SvgRenderer(snapshotDir)

    val preferredSize = panel.preferredSize
    panel.setBounds(0, 0, Math.max(preferredSize.width, JBUI.scale(480)), Math.max(preferredSize.height, 320))
    panel.doLayout()

    val errors = SmartList<Throwable>()

    try {
      compareSvgSnapshot(svgRenderer.svgFileDir.resolve("$snapshotName.svg"), svgRenderer.render(panel), isUpdateSnapshotsGlobal)
    }
    catch (e: MultipleFailureException) {
      errors.addAll(e.failures)
    }
    catch (e: Throwable) {
      errors.add(e)
    }

    MultipleFailureException.assertEmpty(errors)
  }
}