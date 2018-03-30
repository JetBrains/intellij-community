// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.layout

import com.intellij.ide.ui.laf.IntelliJLaf
import com.intellij.ide.ui.laf.darcula.DarculaLaf
import com.intellij.openapi.application.invokeAndWaitIfNeed
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.assertions.Assertions
import com.intellij.util.io.exists
import com.intellij.util.io.outputStream
import com.intellij.util.io.sanitizeFileName
import com.intellij.util.io.write
import com.intellij.util.ui.UIUtil
import io.netty.util.internal.SystemPropertyUtil
import net.miginfocom.layout.Grid
import net.miginfocom.layout.LayoutUtil
import net.miginfocom.swing.MigLayout
import org.assertj.core.data.Offset
import org.assertj.swing.assertions.Assertions.assertThat
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.GraphicsEnvironment
import java.awt.image.BufferedImage
import java.nio.file.Path
import java.nio.file.Paths
import javax.imageio.ImageIO
import javax.swing.JFrame
import javax.swing.JPanel
import javax.swing.UIManager
import javax.swing.plaf.metal.MetalLookAndFeel
import kotlin.properties.Delegates

/**
 * Set `test.update.snapshots=true` to automatically update snapshots if need.
 *
 * Checkout git@github.com:develar/intellij-ui-dsl-test-snapshots.git (or create own repo) to some local dir and set env LAYOUT_IMAGE_REPO
 * to use image snapshots.
 */
open class UiDslTest {
  companion object {
    var currentLaf: String? = null

    private val imageDir: String? = System.getenv("LAYOUT_IMAGE_REPO")
  }

  open val lafName = "IntelliJ"

  @Rule
  @JvmField
  val testName = TestName()

  @Before
  fun beforeMethod() {
    assumeTrue(!UsefulTestCase.IS_UNDER_TEAMCITY)

    if (currentLaf != lafName) {
      currentLaf = lafName
      invokeAndWaitIfNeed {
        UIManager.setLookAndFeel(MetalLookAndFeel())
        val laf = if (lafName == "IntelliJ") IntelliJLaf() else DarculaLaf()
        UIManager.setLookAndFeel(laf)

        if (lafName == "Darcula") {
          // static init it is hell - UIUtil static init is called too early, so, call it to init properly
          // (otherwise null stylesheet added and it leads to NPE on set comment text)
          UIManager.getDefaults().put("javax.swing.JLabel.userStyleSheet", UIUtil.JBHtmlEditorKit.createStyleSheet())
        }
      }
    }
  }

  private fun saveImage(file: Path, component: Component) {
    file.outputStream().use {
      ImageIO.write(componentToImage(component), "png", it)
    }
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
    doTest { visualPaddingsPanel()}
  }

  private fun doTest(panelCreator: () -> JPanel) {
    var panel: JPanel by Delegates.notNull()
    val frame = invokeAndWaitIfNeed {
      LayoutUtil.setGlobalDebugMillis(1000)

      panel = panelCreator()

      val frame = JFrame("wrapper")
      frame.isUndecorated = true
      frame.contentPane.add(panel, BorderLayout.CENTER)
      frame.minimumSize = Dimension(480, 320)

      val screenDevices = GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices
      if (SystemInfoRt.isMac && screenDevices != null && screenDevices.size > 1) {
        // use non-Retina
        for (screenDevice in screenDevices) {
          if (!UIUtil.isRetina(screenDevice)) {
            frame.setLocation(screenDevice.defaultConfiguration.bounds.x, frame.y)
            break
          }
        }
      }

      frame.pack()
      frame.isVisible = true

      // clear focus from first input field
      frame.requestFocusInWindow()

      frame
    }

    val component = panel
    val layout = component.layout as MigLayout

    val gridField = MigLayout::class.java.getDeclaredField("grid")
    gridField.isAccessible = true
    val grid = gridField.get(layout) as Grid
    val rectangles = MigLayoutTestUtil.getRectangles(grid)

    val imageName = sanitizeFileName(testName.methodName)
    val actualSerializedLayout = configurationToJson(component, component.layout as MigLayout,
                                                     rectangles.joinToString(", ") { "[${it.joinToString(", ")}]" })
    try {
      val expectedLayoutDataFile = Paths.get(PlatformTestUtil.getPlatformTestDataPath(), "ui", "layout", lafName, "$imageName.yml")
      val isUpdateSnapshots = SystemPropertyUtil.getBoolean("test.update.snapshots", false)
      if (!expectedLayoutDataFile.exists() || isUpdateSnapshots) {
        expectedLayoutDataFile.write(actualSerializedLayout)
      }
      else {
        Assertions.assertThat(actualSerializedLayout).isEqualTo(expectedLayoutDataFile)
      }

      if (imageDir.isNullOrEmpty()) {
        return
      }

      val imagePath = Paths.get(imageDir, lafName, "$imageName.png")
      if (!imagePath.exists()) {
        System.out.println("Write a new snapshot image ${imagePath.fileName}")
        saveImage(imagePath, frame)
        return
      }

      val newImage = ImageIO.read(imagePath.toFile())
      @Suppress("UnnecessaryVariable")
      val snapshotComponent = frame
      try {
        assertThat(componentToImage(snapshotComponent)).isEqualTo(newImage, Offset.offset(8))
      }
      catch (e: AssertionError) {
        if (isUpdateSnapshots) {
          System.out.println("UPDATED snapshot image ${imagePath.fileName}")
          imagePath.outputStream().use {
            ImageIO.write(componentToImage(snapshotComponent), "png", it)
          }
        }
        else {
          throw e
        }
      }
    }
    catch (e: AssertionError) {
      if (!imageDir.isNullOrEmpty()) {
        Paths.get(imageDir, "$imageName-NEW.yml").write(actualSerializedLayout)
        saveImage(Paths.get(imageDir, "$imageName-NEW.png"), frame)
      }
      throw e
    }

    frame.isVisible = false
    frame.dispose()
  }
}

class DarculaUiDslTest : UiDslTest() {
  override val lafName = "Darcula"
}

private fun componentToImage(component: Component, type: Int = BufferedImage.TYPE_BYTE_GRAY): BufferedImage {
  return invokeAndWaitIfNeed {
    // we don't need retina image
    val image = BufferedImage(component.width, component.height, type)
    val g = image.graphics
    component.paint(g)
    g.dispose()
    image
  }
}