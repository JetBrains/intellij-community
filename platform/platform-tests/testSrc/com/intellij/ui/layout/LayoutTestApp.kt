// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.layout

import com.intellij.ide.ui.laf.IntelliJLaf
import com.intellij.ide.ui.laf.darcula.DarculaLaf
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.ui.components.dialog
import com.intellij.ui.layout.migLayout.*
import com.intellij.ui.layout.migLayout.patched.*
import com.intellij.util.io.write
import com.intellij.util.ui.UIUtil
import net.miginfocom.layout.AC
import net.miginfocom.layout.LayoutUtil
import java.awt.Dimension
import java.awt.GraphicsEnvironment
import java.awt.Point
import java.nio.file.Paths
import javax.swing.*
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
//          val panel = alignFieldsInTheNestedGrid()
//          val panel = visualPaddingsPanelOnlyTextField()
//          val panel = visualPaddingsPanelOnlyLabeledScrollPane()
    //      val panel = labelRowShouldNotGrow()
          val panel = commentAndPanel()
//          val panel = alignFieldsInTheNestedGrid()
//          val panel = visualPaddingsPanel()
//          val panel = withVerticalButtons()
//    val panel = createLafTestPanel()

    val dialog = dialog(
      title = "",
      panel = panel,
      resizable = true,
      okActionEnabled = false
    ) {
      return@dialog null
    }

    panel.preferredSize = Dimension(350, 250)
    if (panel.layout is MigLayout) {
      Paths.get(System.getProperty("user.home"), "layout-dump.yml").write(serializeLayout(panel, isIncludeCellBounds = false))
    }

    moveToNotRetinaScreen(dialog)
    dialog.show()
  }
}

@Suppress("unused")
fun simplePanel() {
  val panel = JPanel(MigLayout(createLayoutConstraints().insets("3px"), AC(), AC().align("baseline")))

  panel.add(JLabel("text"))
  val component = JTextField("text")
  //val ff = component.getBaseline(40, 21)
  panel.add(component)
}

private fun moveToNotRetinaScreen(dialog: DialogWrapper) {
  val screenDevices = GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices
  if (!SystemInfoRt.isMac || screenDevices == null || screenDevices.size <= 1) {
    return
  }

  for (screenDevice in screenDevices) {
    if (!UIUtil.isRetina(screenDevice)) {
      val screenBounds = screenDevice.defaultConfiguration.bounds
      dialog.setInitialLocationCallback {
        val preferredSize = dialog.preferredSize
        Point(screenBounds.x + ((screenBounds.width - preferredSize.width) / 2), (screenBounds.height - preferredSize.height) / 2)
      }
      break
    }
  }
}