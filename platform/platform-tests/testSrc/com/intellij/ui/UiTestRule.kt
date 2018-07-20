// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui

import com.intellij.util.SmartList
import com.intellij.util.ui.JBUI
import org.apache.xmlgraphics.java2d.GenericGraphicsDevice
import org.apache.xmlgraphics.java2d.GraphicsConfigurationWithTransparency
import org.junit.rules.TestName
import org.junit.runners.model.MultipleFailureException
import java.awt.Rectangle
import java.nio.file.Path
import javax.swing.JPanel

class UiTestRule(private val testDataRoot: Path) : RequireHeadlessMode() {
  // must be lazy, otherwise we cannot change `java.awt.headless`
  private val graphicsConfiguration by lazy {
    object : GraphicsConfigurationWithTransparency() {
      private val device = object : GenericGraphicsDevice(this) {
        override fun getType() = TYPE_RASTER_SCREEN
      }

      override fun getBounds() = Rectangle(0, 0, 1000, 1000)

      override fun getDevice() = device
    }
  }

  fun validate(panel: JPanel, testName: TestName, lafName: String) {
    validate(panel, testName.snapshotFileName, lafName)
  }

  fun validate(panel: JPanel, snapshotName: String, lafName: String) {
    val snapshotDir = testDataRoot.resolve(getSnapshotRelativePath(lafName))
    val svgRenderer = SvgRenderer(snapshotDir, graphicsConfiguration)

    // to run tests on retina monitor (@2x images must be not used and so on)
    // actually, not required (IconUtil correctly uses graphics device configuration), but just to be sure
    AppUIUtil.setGraphicsConfiguration(panel, graphicsConfiguration)

    val preferredSize = panel.preferredSize
    panel.setBounds(0, 0, Math.max(preferredSize.width, JBUI.scale(480)), Math.max(preferredSize.height, 320))
    panel.doLayout()

    val errors = SmartList<Throwable>()

    try {
      validateBounds(panel, snapshotDir, snapshotName)
    }
    catch (e: Throwable) {
      errors.add(e)
    }

    try {
      compareSvgSnapshot(svgRenderer.svgFileDir.resolve("$snapshotName.svg"), svgRenderer.render(panel), isUpdateSnapshotsGlobal)
    }
    catch (e: Throwable) {
      errors.add(e)
    }

    MultipleFailureException.assertEmpty(errors)
  }
}