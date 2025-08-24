// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.compose.showcase

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import org.jetbrains.idea.devkit.util.PsiUtil
import org.jetbrains.jewel.bridge.compose
import org.jetbrains.jewel.ui.component.Slider
import org.jetbrains.jewel.ui.component.Text
import java.awt.BorderLayout
import java.util.*
import javax.swing.*
import kotlin.math.*

internal class ComposePerformanceDemoAction : DumbAwareAction() {

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = e.project != null && PsiUtil.isPluginProject(e.project!!)
  }

  override fun actionPerformed(e: AnActionEvent) {
    val dialog = MyDialog(e.project, e.presentation.text)
    dialog.show()
  }
}

@Suppress("HardCodedStringLiteral")
private class MyDialog(project: Project?, dialogTitle: String) :
  DialogWrapper(project, null, true, IdeModalityType.MODELESS, true) {
  val centerPanelWrapper = JPanel(BorderLayout())

  enum class TestCase { TextAnimation, Canvas }
  enum class Mode { Swing, AWT }

  var mode = Mode.Swing
  var testCase = TestCase.Canvas

  init {
    title = dialogTitle
    setSize(800, 600)
    init()
  }

  override fun createCenterPanel(): JComponent {
    initCentralPanel()
    return centerPanelWrapper
  }

  override fun createSouthPanel(): JComponent {
    val controlPanel = JPanel()
    controlPanel.layout = BoxLayout(controlPanel, BoxLayout.X_AXIS)
    controlPanel.add(JLabel("Test case: "))

    ButtonGroup().let { group ->
      val textAnimationButton = JRadioButton("Text animation")
      val canvasButton = JRadioButton("Canvas")
      group.add(textAnimationButton)
      group.add(canvasButton)

      textAnimationButton.isSelected = testCase == TestCase.TextAnimation
      canvasButton.isSelected = testCase == TestCase.Canvas

      textAnimationButton.addActionListener { testCase = TestCase.TextAnimation; initCentralPanel() }
      canvasButton.addActionListener { testCase = TestCase.Canvas; initCentralPanel() }

      controlPanel.add(canvasButton)
      controlPanel.add(textAnimationButton)
    }

    controlPanel.add(JSeparator(JSeparator.VERTICAL))

    controlPanel.add(JLabel("Mode: "))
    ButtonGroup().let { group ->
      val swingModeButton = JRadioButton("Swing(lightweight)")
      val awtModeButton = JRadioButton("AWT(heavyweight)")
      group.add(swingModeButton)
      group.add(awtModeButton)

      swingModeButton.isSelected = mode == Mode.Swing
      awtModeButton.isSelected = mode == Mode.AWT

      swingModeButton.addActionListener { mode = Mode.Swing; initCentralPanel() }
      awtModeButton.addActionListener { mode = Mode.AWT; initCentralPanel() }

      controlPanel.add(swingModeButton)
      controlPanel.add(awtModeButton)
    }

    return controlPanel
  }

  private fun initCentralPanel() {
    val swingModeValue = System.getProperty("compose.swing.render.on.graphics")
    System.setProperty("compose.swing.render.on.graphics", (mode == Mode.Swing).toString().lowercase())

    centerPanelWrapper.components.forEach { c -> c.isVisible = false }

    centerPanelWrapper.removeAll()
    val comp = when (testCase) {
      TestCase.TextAnimation -> createTextAnimationComponent()
      TestCase.Canvas -> createClockComponent()
    }

    centerPanelWrapper.add(comp)
    centerPanelWrapper.revalidate()
    centerPanelWrapper.repaint()

    System.setProperty("compose.swing.render.on.graphics", swingModeValue)
  }
}

private fun createClockComponent(): JComponent {
  return compose {
    val mode = if (System.getProperty("compose.swing.render.on.graphics", "false").toBoolean()) "Swing" else "AWT"
    val frameTimesNano = remember<LinkedList<Long>> { LinkedList() }
    val systemTime = remember { mutableStateOf(System.currentTimeMillis()) }
    val textMeasurer = rememberTextMeasurer()
    val dialSize = remember { mutableStateOf(40f) }
    LaunchedEffect(Unit) {
      while (true) {
        systemTime.value = System.currentTimeMillis()
        val timeNowNano = System.nanoTime()
        frameTimesNano.add(timeNowNano)
        frameTimesNano.removeAll { it < timeNowNano - 1_000_000_000 }
        withFrameNanos { }
      }
    }
    Column(
      modifier = Modifier.fillMaxWidth().padding(16.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Canvas(modifier = Modifier.fillMaxSize()) {
        val canvasWidth = size.width
        val canvasHeight = size.height

        val scale = density

        val watchFill = Color.White
        val watchStroke = Color.Red
        val watchStrokeAA = Color.Black

        val strokeWidth = 1f * scale
        val diameter = dialSize.value * scale
        val gridStep = diameter * 1.2

        val fontSize = 15

        val statusTextHeight = fontSize * scale * 1.2 * 2
        val offsetX = (5 * scale).toInt()
        val offsetY = (5 * scale + statusTextHeight).toInt()

        val speedUp = 60 * 10
        val currentTimeMs = systemTime.value * speedUp

        var primitivesAmount = 0

        var i = 0
        for (x in offsetX until canvasWidth.toInt() step gridStep.roundToInt()) {
          var j = 0
          for (y in offsetY until canvasHeight.toInt() step gridStep.roundToInt()) {
            val strokeColor = if (x > canvasWidth / 2) watchStrokeAA else watchStroke

            drawOval(
              color = watchFill,
              topLeft = Offset(x.toFloat(), y.toFloat()),
              size = Size(diameter, diameter)
            )
            primitivesAmount++

            drawOval(
              color = strokeColor,
              topLeft = Offset(x.toFloat(), y.toFloat()),
              size = Size(diameter, diameter),
              style = Stroke(width = strokeWidth)
            )
            primitivesAmount++

            val centerX = x + diameter / 2
            val centerY = y + diameter / 2

            var divisionAngle = 0f
            while (divisionAngle < 2f * PI) {
              drawLine(
                color = strokeColor,
                start = Offset(
                  centerX - diameter * 0.4f * sin(divisionAngle),
                  centerY + diameter * 0.4f * cos(divisionAngle),
                ),
                end = Offset(
                  centerX - diameter * 0.5f * sin(divisionAngle),
                  centerY + diameter * 0.5f * cos(divisionAngle),
                ),
                strokeWidth = strokeWidth
              )
              primitivesAmount++
              divisionAngle += (2.0 * PI / 12.0).toFloat()
            }

            val currentTimeMilliMin = currentTimeMs / 60 + (j * 5 + i * 5) * 1000
            val currentTimeMilliHour = currentTimeMilliMin / 60
            val minAngle = ((currentTimeMilliMin % 60000) / 60f / 1000f) * 2f * PI.toFloat()
            val hourAngle = ((currentTimeMilliHour % 12000) / 12f / 1000f) * 2f * PI.toFloat()

            drawLine(
              color = strokeColor,
              start = Offset(centerX, centerY),
              end = Offset(
                centerX - diameter * 0.35f * sin(minAngle),
                centerY + diameter * 0.35f * cos(minAngle)
              ),
              strokeWidth = strokeWidth
            )
            primitivesAmount++

            drawLine(
              color = strokeColor,
              start = Offset(centerX, centerY),
              end = Offset(
                centerX - diameter * 0.25f * sin(hourAngle),
                centerY + diameter * 0.25f * cos(hourAngle),
              ),
              strokeWidth = strokeWidth
            )
            primitivesAmount++

            ++j
          }
          ++i
        }
        drawText(
          text = "Dial size: ${dialSize.value} Mode: $mode | Primitives: $primitivesAmount | Canvas: ${canvasWidth.toInt()}x${canvasHeight.toInt()} | Scale: $scale\nFPS: ${frameTimesNano.size}",
          topLeft = Offset(0f, 0f),
          style = TextStyle(
            color = Color.Red,
            fontSize = fontSize.sp,
          ),
          textMeasurer = textMeasurer,
          softWrap = false,
          overflow = TextOverflow.Clip,
          maxLines = 2
        )
      }
      val sizeValue = listOf(5, 10, 20, 40, 80) + (120..600 step 40).toList() + (180..1200 step 60).toList()
      Slider(
        value = dialSize.value,
        onValueChange = { newValue -> dialSize.value = (sizeValue.minByOrNull { abs(it - newValue) } ?: newValue).toFloat() },
        valueRange = sizeValue.minOf { it.toFloat() }..sizeValue.maxOf { it.toFloat() },
        modifier = Modifier.fillMaxWidth()
      )
    }
  }
}

private fun createTextAnimationComponent(): JComponent {
  return compose {
    val frameTimes = remember { LinkedList<Long>() }

    val infiniteTransition = rememberInfiniteTransition()
    val hue by infiniteTransition.animateFloat(
      initialValue = 0f,
      targetValue = 360f,
      animationSpec = infiniteRepeatable(
        animation = tween(3000, easing = LinearEasing),
        repeatMode = RepeatMode.Restart
      )
    )

    SideEffect {
      frameTimes.add(System.nanoTime())
      frameTimes.removeAll { it < System.nanoTime() - 1_000_000_000 }
    }

    Text(
      text = "FPS: ${frameTimes.size}",
      modifier = Modifier.padding(50.dp),
      fontSize = 50.sp,
      lineHeight = 60.sp,
      fontWeight = FontWeight.Medium,
      color = Color.hsl(hue, 1f, 0.5f)
    )
  }
}
