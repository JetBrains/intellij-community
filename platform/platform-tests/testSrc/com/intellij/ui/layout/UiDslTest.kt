// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.layout

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import com.google.gson.GsonBuilder
import com.intellij.ui.components.CheckBox
import com.intellij.util.io.exists
import com.intellij.util.io.outputStream
import com.intellij.util.io.sanitizeFileName
import net.miginfocom.layout.Grid
import net.miginfocom.layout.LayoutUtil
import net.miginfocom.swing.MigLayout
import org.assertj.swing.assertions.Assertions.assertThat
import org.assertj.swing.edt.FailOnThreadViolationRepaintManager
import org.assertj.swing.edt.GuiActionRunner
import org.assertj.swing.fixture.FrameFixture
import org.intellij.lang.annotations.Language
import org.junit.*
import org.junit.rules.TestName
import java.awt.*
import java.awt.image.BufferedImage
import java.io.StringReader
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import java.util.concurrent.Callable
import javax.imageio.ImageIO
import javax.swing.JFrame
import javax.swing.JPanel
import javax.swing.JTextField
import kotlin.properties.Delegates

class UiDslTest {
  companion object {
    @Suppress("unused")
    @BeforeClass
    fun setUpOnce() {
      FailOnThreadViolationRepaintManager.install()
    }

    private val imageDir: String? = System.getenv("LAYOUT_IMAGE_REPO")
  }

  private var window: FrameFixture by Delegates.notNull()

  @Rule
  @JvmField
  val testName = TestName()

  @Before
  fun setUp() {
    val frame = GuiActionRunner.execute(Callable {
      LayoutUtil.setGlobalDebugMillis(1000)

      val androidModuleNameComponent = JTextField("input")
      val androidCheckBox = CheckBox("Android module name:")
      val panel = panel {
        row("Create Android module") { androidCheckBox() }
        row("Android module name:") { androidModuleNameComponent() }
      }

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
  }

  @After
  fun tearDown() {
    window.cleanUp()
  }

  private fun saveImage(file: Path) {
    file.outputStream().use {
      ImageIO.write(componentToImage(getContentPane()), "png", it)
    }
  }

  private fun getContentPane(): Container {
    return window.target()
  }

  private fun assertLayout(@Language("JSON") jsonData: String) {
    val component = window.panel("test").target()
    assertThat(configurationToJson(component, (component as JPanel).layout as MigLayout, false)).isEqualTo(jsonData.trimIndent())
  }

  @Test
  fun test() {
    val component = window.panel("test").target()
    val layout = (component as JPanel).layout as MigLayout

    assertLayout("""
{
  "rowConstraints": "",
  "columnConstraints": {
    "count": 3.0,
    "constraints": [
      {},
      {
        "grow": 100.0
      },
      {
        "grow": 100.0
      }
    ]
  },
  "componentConstrains": {
    "JLabel #0": {},
    "JCheckBox #1": {
      "wrap": true
    },
    "JLabel #2": {},
    "JTextField #3": {
      "wrap": true
    }
  }
}""")

    val gridField = MigLayout::class.java.getDeclaredField("grid")
    gridField.isAccessible = true
    val grid = gridField.get(layout) as Grid
    val rectangles = MigLayoutTestUtil.getRectangles(grid)
    try {
      assertThat(rectangles.joinToString(",") { "[${it.joinToString(",")}]" }).isEqualTo(
        "[0,0,145,23],[165,0,251,23],[0,28,145,26],[165,28,251,26]")

      if (imageDir.isNullOrEmpty()) {
        return
      }

      val imagePath = Paths.get(imageDir, "${sanitizeFileName(testName.methodName)}.png")
      if (!imagePath.exists()) {
        System.out.println("Write a new snapshot image ${imagePath.fileName}")
        saveImage(imagePath)
        return
      }

      assertThat(componentToImage(getContentPane())).isEqualTo(ImageIO.read(imagePath.toFile()))
    }
    catch (e: AssertionError) {
      if (!imageDir.isNullOrEmpty()) {
        saveImage(Paths.get(imageDir, "${sanitizeFileName(testName.methodName)}-NEW.png"))
      }
      throw e
    }
  }

  @Suppress("UNCHECKED_CAST")
  private fun configurationToJson(component: JPanel, layout: MigLayout, isIncludeLayoutConstraints: Boolean): String {
    val objectMapper = ObjectMapper()
    objectMapper.setSerializationInclusion(JsonInclude.Include.NON_DEFAULT)

    val componentConstrains = LinkedHashMap<String, Any>()
    for ((index, c) in component.components.withIndex()) {
      componentConstrains.put("${c.javaClass.simpleName} #${index}", layout.getComponentConstraints(c))
    }

    val json = objectMapper
      .writerWithDefaultPrettyPrinter()
      .writeValueAsString(linkedMapOf(
        "layoutConstraints" to if (isIncludeLayoutConstraints) layout.layoutConstraints else null,
        "rowConstraints" to layout.rowConstraints,
        "columnConstraints" to layout.columnConstraints,
        "componentConstrains" to componentConstrains
      ))
    // *** *** jackson has ugly API and not clear how to write custom filter, so, GSON is used
    val gson = GsonBuilder().setPrettyPrinting().create()
    val map = gson.fromJson(StringReader(json), MutableMap::class.java)
    @Suppress("UNCHECKED_CAST")
    for (cc in (map.get("componentConstrains") as MutableMap<String, MutableMap<String, Any?>>).values) {
      removeDefaultCc(cc)
      cc.remove("animSpec")

      for (name in arrayOf("horizontal", "vertical")) {
        val p = cc.get(name) as MutableMap<String, Any?>? ?: continue
        val size = p.get("size") as MutableMap<*, *>?
        if (size != null && size.get("unset") == true) {
          size.remove("unset")
          if (size.isEmpty()) {
            p.remove("size")
          }
        }

        removeDefaultCc(p)
        if (p.isEmpty()) {
          cc.remove(name)
        }
      }
    }

    val columnConstraints = map.get("columnConstraints") as MutableMap<String, Any>
    val acList = columnConstraints.remove("constaints")!! as List<MutableMap<String, Any>>
    columnConstraints.put("constraints", acList)
    for (ac in acList) {
      val size = ac.get("size") as MutableMap<*, *>?
      if (size != null && size.get("unset") == true) {
        size.remove("unset")
        if (size.isEmpty()) {
          ac.remove("size")
        }

        if (ac.get("shrinkPriority") == 100.0) {
          ac.remove("shrinkPriority")
        }
        if (ac.get("shrink") == 100.0) {
          ac.remove("shrink")
        }
        if (ac.get("growPriority") == 100.0) {
          ac.remove("growPriority")
        }
      }
    }

    return gson.toJson(map)
  }

  private fun removeDefaultCc(cc: MutableMap<String, Any?>) {
    for ((name, value) in DEFAULT_CC) {
      if (cc.get(name) == value) {
        cc.remove(name)
      }
    }
  }
}

private fun componentToImage(component: Component): BufferedImage {
  // we don't need retina image
  val image = BufferedImage(component.width, component.height, BufferedImage.TYPE_BYTE_GRAY)
  val g = image.graphics
  component.paint(g)
  g.dispose()
  return image
}

private val DEFAULT_CC = mapOf(
  "dockSide" to -1.0,
  "split" to 1.0,
  "spanX" to 1.0,
  "spanY" to 1.0,
  "cellX" to -1.0,
  "cellY" to -1.0,
  "hideMode" to -1.0,
  "growPriority" to 100.0,
  "shrinkPriority" to 100.0,
  "shrink" to 100.0,
  "grow" to 100.0,
  "boundsInGrid" to true,
  "" to ""
)