// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.layout

import com.intellij.ide.ui.laf.IntelliJLaf
import com.intellij.openapi.application.invokeAndWaitIfNeed
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.assertions.Assertions
import com.intellij.util.io.exists
import com.intellij.util.io.outputStream
import com.intellij.util.io.sanitizeFileName
import com.intellij.util.io.write
import io.netty.util.internal.SystemPropertyUtil
import net.miginfocom.layout.Grid
import net.miginfocom.layout.LayoutUtil
import net.miginfocom.swing.MigLayout
import org.assertj.core.data.Offset
import org.assertj.swing.assertions.Assertions.assertThat
import org.assertj.swing.edt.FailOnThreadViolationRepaintManager
import org.assertj.swing.edt.GuiActionRunner
import org.assertj.swing.fixture.FrameFixture
import org.junit.*
import org.junit.Assume.assumeTrue
import org.junit.rules.TestName
import java.awt.*
import java.awt.image.BufferedImage
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.Callable
import javax.imageio.ImageIO
import javax.swing.JFrame
import javax.swing.JPanel
import javax.swing.UIManager
import kotlin.properties.Delegates

/**
 * Set `test.update.snapshots=true` to automatically update snapshots if need.
 *
 * Checkout git@github.com:develar/intellij-ui-dsl-test-snapshots.git (or create own repo) to some local dir and set env LAYOUT_IMAGE_REPO
 * to use image snapshots.
 */
class UiDslTest {
  companion object {
    @Suppress("unused")
    @BeforeClass
    fun setUpOnce() {
      FailOnThreadViolationRepaintManager.install()
      UIManager.setLookAndFeel(IntelliJLaf())
    }

    private val imageDir: String? = System.getenv("LAYOUT_IMAGE_REPO")
  }

  private var window: FrameFixture by Delegates.notNull()

  @Rule
  @JvmField
  val testName = TestName()

  @After
  fun tearDown() {
    window.cleanUp()
  }

  @Before
  fun beforeMethod() {
    assumeTrue(!UsefulTestCase.IS_UNDER_TEAMCITY)
  }

  private fun saveImage(file: Path) {
    file.outputStream().use {
      ImageIO.write(componentToImage(getContentPane()), "png", it)
    }
  }

  private fun getContentPane(): Container {
    return window.target()
  }

  @Test
  fun `align fields in the nested grid`() {
    doTest(alignFieldsInTheNestedGrid())
  }

  @Test
  fun `align fields`() {
    doTest(labelRowShouldNotGrow())
  }

  @Test
  fun cell() {
    doTest(cellPanel())
  }

  @Test
  fun `note row in the dialog`() {
    doTest(noteRowInTheDialog())
  }

  private fun doTest(panel: JPanel) {
    val frame = GuiActionRunner.execute(Callable {
      LayoutUtil.setGlobalDebugMillis(1000)

      panel.background = Color.WHITE
      panel.name = "test"

      val frame = JFrame("wrapper")
      frame.isUndecorated = true
      frame.contentPane.background = Color.WHITE
      frame.background = Color.WHITE
      frame.contentPane.add(panel, BorderLayout.CENTER)
      frame.minimumSize = Dimension(512, 256)
      frame
    })
    window = FrameFixture(frame)
    window.show()

    val component = window.panel("test").target() as JPanel
    val layout = component.layout as MigLayout

    val gridField = MigLayout::class.java.getDeclaredField("grid")
    gridField.isAccessible = true
    val grid = gridField.get(layout) as Grid
    val rectangles = MigLayoutTestUtil.getRectangles(grid)

    val imageName = sanitizeFileName(testName.methodName)
    val actualLayoutJson = configurationToJson(component, component.layout as MigLayout,
                                               rectangles.joinToString(", ") { "[${it.joinToString(", ")}]" })
    try {
      val expectedLayoutDataFile = Paths.get(PlatformTestUtil.getPlatformTestDataPath(), "ui", "layout", "$imageName.yml")
      val isUpdateSnapshots = SystemPropertyUtil.getBoolean("test.update.snapshots", false)
      if (!expectedLayoutDataFile.exists() || isUpdateSnapshots) {
        expectedLayoutDataFile.write(actualLayoutJson)
      }
      else {
        Assertions.assertThat(actualLayoutJson).isEqualTo(expectedLayoutDataFile)
      }

      if (imageDir.isNullOrEmpty()) {
        return
      }

      val imagePath = Paths.get(imageDir, "$imageName.png")
      if (!imagePath.exists()) {
        System.out.println("Write a new snapshot image ${imagePath.fileName}")
        saveImage(imagePath)
        return
      }

      val newImage = ImageIO.read(imagePath.toFile())
      try {
        assertThat(componentToImage(getContentPane())).isEqualTo(newImage, Offset.offset(32))
      }
      catch (e: AssertionError) {
        if (isUpdateSnapshots) {
          System.out.println("UPDATED snapshot image ${imagePath.fileName}")
          imagePath.outputStream().use {
            ImageIO.write(componentToImage(getContentPane()), "png", it)
          }
        }
        else {
          throw e
        }
      }
    }
    catch (e: AssertionError) {
      if (!imageDir.isNullOrEmpty()) {
        Paths.get(imageDir, "$imageName-NEW.yml").write(actualLayoutJson)
        saveImage(Paths.get(imageDir, "$imageName-NEW.png"))
      }
      throw e
    }
  }
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