// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui

import com.intellij.testFramework.UsefulTestCase
import com.intellij.util.SmartList
import com.intellij.util.ui.JBUI
import org.junit.rules.TestName
import org.junit.runners.model.MultipleFailureException
import java.nio.file.Path
import javax.swing.JPanel

class UiTestRule(private val testDataRoot: Path) : RequireHeadlessMode() {
  override fun before() {
    super.before()

    IconManager.activate()
  }

  override fun after() {
    super.after()

    IconManager.deactivate()
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
      validateBounds(panel, snapshotDir, snapshotName)
    }
    catch (e: Throwable) {
      if (UsefulTestCase.IS_UNDER_TEAMCITY) {
        // TC doesn't support MultipleFailureException correctly
        throw e
      }

      errors.add(e)
    }

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