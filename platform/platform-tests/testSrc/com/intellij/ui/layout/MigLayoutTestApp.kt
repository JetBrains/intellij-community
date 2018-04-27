// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.layout

import com.intellij.ide.ui.laf.IntelliJLaf
import com.intellij.ide.ui.laf.darcula.DarculaLaf
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.ui.components.dialog
import com.intellij.util.io.write
import com.intellij.util.ui.UIUtil
import net.miginfocom.layout.LayoutUtil
import net.miginfocom.swing.MigLayout
import java.awt.Dimension
import java.awt.GraphicsEnvironment
import java.nio.file.Paths
import javax.swing.JComboBox
import javax.swing.LookAndFeel
import javax.swing.UIManager
import javax.swing.plaf.metal.MetalLookAndFeel

object DarculaUiTestApp {
  @JvmStatic
  fun main(args: Array<String>) {
    run(DarculaLaf())
  }
}

object IntelliJUiTestApp {
  @JvmStatic
  fun main(args: Array<String>) {
    run(IntelliJLaf())
  }
}

private fun run(laf: LookAndFeel) {
  val isDebugEnabled = true
  //    val isDebugEnabled = false
  @Suppress("ConstantConditionIf")
  if (isDebugEnabled) {
    LayoutUtil.setGlobalDebugMillis(1000)
  }

  runInEdtAndWait {
    UIManager.setLookAndFeel(MetalLookAndFeel())
    UIManager.setLookAndFeel(laf)
    //      UIManager.setLookAndFeel(DarculaLaf())

    //      val panel = visualPaddingsPanelOnlyButton()
    //      val panel = visualPaddingsPanelOnlyComboBox()
    //      val panel = alignFieldsInTheNestedGrid()
    //      val panel = labelRowShouldNotGrow()
    //      val panel = cellPanel()
          val panel = visualPaddingsPanel()
//    val panel = createLafTestPanel()

//    val jTextArea = JTextArea("wefwg w wgw")
//    jTextArea.rows = 3
//    val panel = UI.PanelFactory
//      .panel(JBScrollPane(jTextArea))
//      .withLabel("Foo")
//      .createPanel()

//    val panel = UI.PanelFactory
//      .panel(JTextField("wfgw"))
//      .withLabel("Foo")
//      .createPanel()

    val editableCombobox = JComboBox<String>(arrayOf("one", "two"))
    editableCombobox.isEditable = true

    //      val panel = JPanel(VerticalFlowLayout())
    //      panel.add(JComboBox<String>(arrayOf("one", "two")))
    //      panel.add(editableCombobox)

    val dialog = dialog(
      title = "",
      panel = panel,
      resizable = true,
      okActionEnabled = false
    ) {
      return@dialog null
    }

    panel.preferredSize = Dimension(50, 50)
    if (panel.layout is MigLayout) {
      Paths.get(System.getProperty("user.home"), "layout-dump.yml").write(serializeLayout(panel, isIncludeCellBounds = false))
    }

    val screenDevices = GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices
    if (SystemInfoRt.isMac && screenDevices != null && screenDevices.size > 1) {
      // use not-Retina
      for (screenDevice in screenDevices) {
        if (!UIUtil.isRetina(screenDevice)) {
          val screenBounds = screenDevice.defaultConfiguration.bounds
          dialog.setLocation(screenBounds.x, (screenBounds.height - dialog.preferredSize.height) / 2)
          dialog.window.setLocation(screenBounds.x, (screenBounds.height - dialog.preferredSize.height) / 2)
          break
        }
      }
    }

    //      dialog.toFront()
    dialog.showAndGet()

    //    val frame = JFrame()
    //    frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
    //    frame.contentPane.add(panel, BorderLayout.CENTER)
    //    frame.contentPane.background = Color.WHITE
    //    frame.background = Color.WHITE
    //    frame.pack()
    //    frame.setLocationRelativeTo(null)
    //    frame.minimumSize = Dimension(512, 256)
    //    frame.isVisible = true
    //
  }
}