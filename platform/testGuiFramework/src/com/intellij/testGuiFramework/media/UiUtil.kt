/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.testGuiFramework.media

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.testGuiFramework.media.UiUtil.showPlayback
import com.intellij.testGuiFramework.test.ParameterHintsDemo
import com.intellij.ui.components.labels.ActionLink
import com.sun.awt.AWTUtilities
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseMotionAdapter
import java.awt.font.TextAttribute
import java.io.File
import java.util.*
import javax.imageio.ImageIO
import javax.swing.BoxLayout
import javax.swing.JFrame
import javax.swing.JPanel
import kotlin.concurrent.thread


object UiUtil {


  private var playBackPanel: MotionPanel? = null
  private var percentage: Double = 0.0

  fun showSplash() {
    val frame = JFrame()
    frame.isUndecorated = true

    val path = ParameterHintsDemo::class.java.getResource("/splash.png").toURI().toURL().path
    val image = ImageIO.read(File(path));

    val panel = object : JPanel() {
      override fun paintComponent(g: Graphics?) {
        super.paintComponent(g)
        g!!.drawImage(image, 0, 0, this)
      }
    }


    panel.isOpaque = false

    frame.setSize(640, 360)
    val dim = Toolkit.getDefaultToolkit().screenSize
    frame.setLocation(dim.width / 2 - frame.getSize().width / 2, dim.height / 2 - frame.getSize().height / 2)
    frame.add(panel)
    frame.isVisible = true

    thread(name = "IDE Presenter Decorate Thread") {
      for (i in 100 downTo 0) {
        AWTUtilities.setWindowOpacity(frame, (1.0f * i) / 100)
        Thread.sleep(30)
      }
      frame.isVisible = false
      frame.dispose()
    }
  }

  fun showMenu() {
    val frame = JFrame()
    frame.isUndecorated = true

    val path = ParameterHintsDemo::class.java.getResource("/whats_new.png").toURI().toURL().path
    val image = ImageIO.read(File(path));

    val panel = object : JPanel() {
      override fun paintComponent(g: Graphics?) {
        super.paintComponent(g)
        g!!.drawImage(image, 0, 0, this)
      }
    }
    panel.isOpaque = false
    panel.setLayout(null)

    val menuPanel = JPanel()
    menuPanel.isOpaque = false
    menuPanel.layout = BoxLayout(menuPanel, BoxLayout.PAGE_AXIS)

    menuPanel.add(actionLink(frame, "1. Debugger improvements"))
    menuPanel.add(actionLink(frame, "2. VCS improvements"))
    menuPanel.add(actionLink(frame, "3. Parameter hints"))
    menuPanel.add(actionLink(frame, "4. Semantic highlighting"))
    menuPanel.add(actionLink(frame, "5. Refactoring to Java 8"))
    menuPanel.add(actionLink(frame, "6. Gradle improvements"))
    menuPanel.add(actionLink(frame, "7. JavaScript improvements"))
    menuPanel.add(actionLink(frame, "8. Database improvements"))

    panel.add(menuPanel)
    menuPanel.setLocation(50, 160)
    menuPanel.setSize(500, 300)

    frame.setSize(845, 475)
    val dim = Toolkit.getDefaultToolkit().screenSize
    frame.setLocation(dim.width / 2 - frame.getSize().width / 2, dim.height / 2 - frame.getSize().height / 2)
    frame.add(panel)
    frame.isVisible = true

  }

  fun actionLink(frame: JFrame, text: String): ActionLink {
    val wrapAction = object : AnAction() {
      override fun actionPerformed(e: AnActionEvent?) {
        thread(name = "IDE Presenter Decorate Thread") {
          for (i in 100 downTo 0) {
            AWTUtilities.setWindowOpacity(frame, (1.0f * i) / 100)
            Thread.sleep(10)
          }
          frame.isVisible = false
          frame.dispose()
        }
        DemoAction().actionPerformed(e)
      }
    }
    val actionLink = ActionLink(text, wrapAction)
    actionLink.setNormalColor(Color(230, 230, 230))
    actionLink.activeColor = Color(255, 255, 255)
    actionLink.setVisitedColor(Color(230, 230, 230))
    actionLink.setPaintUnderline(false)

    val attributes = LinkedHashMap<TextAttribute, Any>()

    attributes.put(TextAttribute.FAMILY, Font.SANS_SERIF);
    attributes.put(TextAttribute.WEIGHT, TextAttribute.WEIGHT_EXTRABOLD);
    attributes.put(TextAttribute.SIZE, 20);

    actionLink.font = Font.getFont(attributes)
    return actionLink
  }

  fun Graphics.drawSlider(percentage: Double) {
    val x0 = 55
    val y0 = 45
    val x1 = 381
    val width = 4
    val height = 10
    this.color = Color(240, 240, 240)
    val x = ((x1 - x0) * percentage + x0).toInt()
    this.fillRect(x - width / 2, y0 - height / 2, width, height)
  }


  fun showPlayback() {
    val frame = JFrame()
    frame.isUndecorated = true
    frame.isAlwaysOnTop = true

    val path = ParameterHintsDemo::class.java.getResource("/playback.png").toURI().toURL().path
    val image = ImageIO.read(File(path));

    playBackPanel = object : MotionPanel(frame) {
      override fun paintComponent(g: Graphics?) {
        super.paintComponent(g)
        g!!.drawImage(image, 0, 0, this)
        g.color = Color(240, 240, 240)
        g.drawSlider(percentage)
      }
    }
    playBackPanel!!.isOpaque = false
    playBackPanel!!.setLayout(null)
    frame.setSize(440, 64)

    val dim = Toolkit.getDefaultToolkit().screenSize
    frame.setLocation(10, dim.height - frame.getSize().height - 20)
    frame.add(playBackPanel)
    AWTUtilities.setWindowOpacity(frame, 0.7f)
    frame.isVisible = true
  }

  fun progress(newValue: Double) {
    check(newValue >= 0.0)
    check(newValue <= 1.0)
    percentage = newValue
    playBackPanel?.repaint()
  }

}

open class MotionPanel(private val parent: JFrame) : JPanel() {
  private var initialClick: Point? = null

  init {

    addMouseListener(object : MouseAdapter() {
      override fun mousePressed(e: MouseEvent) {
        if (e.button != MouseEvent.BUTTON1) {
          parent.isVisible = false
          parent.dispose()
        }
        else {
          initialClick = e.getPoint()
          getComponentAt(initialClick!!)
        }
      }
    })

    addMouseMotionListener(object : MouseMotionAdapter() {
      override fun mouseDragged(e: MouseEvent) {

        // get location of Window
        val thisX = parent.location.x
        val thisY = parent.location.y

        // Determine how much the mouse moved since the initial click
        val xMoved = thisX + e.getX() - (thisX + initialClick!!.x)
        val yMoved = thisY + e.getY() - (thisY + initialClick!!.y)

        // Move window to this position
        val X = thisX + xMoved
        val Y = thisY + yMoved
        parent.setLocation(X, Y)
      }
    })

  }
}

fun main(args: Array<String>) {
  showPlayback()
}
