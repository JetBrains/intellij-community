// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.ui

import com.intellij.icons.AllIcons
import com.intellij.ide.ui.LafManager
import com.intellij.ide.ui.UISettings
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.Disposer
import com.intellij.ui.*
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.IconUtil
import com.intellij.util.ui.*
import java.awt.Component
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Rectangle
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JList
import kotlin.math.ceil

class ShowIconScaleTestAction : DumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    IconScaleTestDialog(e.project).show()
  }
}

private class IconScaleTestDialog(project: Project?) : DialogWrapper(project) {
  private lateinit var panel: Placeholder

  private var objectScale: Float = 1.0f
  private var passObjectScale: Boolean = true
  private var passContextContext: Boolean = true
  private var wrapDeferred: Boolean = false
  private var markScalable: Boolean = true

  private lateinit var rawPlatformIcon: Icon
  private lateinit var rawScaledIcon: Icon
  private lateinit var rawCachingIcon: Icon
  private lateinit var rawPlainIcon: Icon

  init {
    title = "Icons Scale Test"
    init()

    val originalScale = JBUIScale.scale(1.0f)
    Disposer.register(disposable) {
      JBUIScale.setUserScaleFactor(originalScale)

      UISettings.getInstance().fireUISettingsChanged()
      LafManager.getInstance().updateUI()
      EditorUtil.reinitSettings()
    }
  }

  override fun createCenterPanel(): JComponent {
    val contentPanel = panel {
      row {
        panel = placeholder().align(Align.FILL)

        rebuildIcons()
        rebuildUi()
      }.resizableRow()
    }
    return ScrollPaneFactory.createScrollPane(contentPanel, true)
  }

  private fun rebuildIcons() {
    val size = 16 // the default size of used sample platform icon
    rawPlatformIcon = buildIcon(size) { AllIcons.General.GearPlain }
    rawScaledIcon = buildIcon(size) {
      ScaledCircleIcon(size).also {
        if (markScalable) JBUIScale.scaleIcon(it)
      }
    }
    rawCachingIcon = buildIcon(size) {
      CachingCircleIcon(size).also {
        if (markScalable) JBUIScale.scaleIcon(it)
      }
    }
    rawPlainIcon = buildIcon(size) { PlainCircleIcon(size) }
  }

  private fun rebuildUi() {
    val sysScale = JBUIScale.sysScale()
    val userScale = JBUIScale.scale(1.0f)
    val objectScale = objectScale

    val icons = mutableMapOf<String, Icon>()
    icons["Platform"] = prepareIcon(rawPlatformIcon)
    icons["Scaled"] = prepareIcon(rawScaledIcon)
    icons["Caching"] = prepareIcon(rawCachingIcon)
    icons["Plain"] = prepareIcon(rawPlainIcon)

    panel.component = panel {
      row {
        comment("""
          SYS_SCALE = $sysScale<br/>
          USER_SCALE = $userScale<br/>
          OBJ_SCALE = $objectScale
        """.trimIndent())

        button("Rebuild Icons") {
          rebuildIcons()
          rebuildUi()
        }
        button("Rebuild UI") {
          rebuildUi()
        }
      }

      group("Options") {
        row {
          checkBox("Pass object scale:")
            .selected(passObjectScale)
            .applyToComponent {
              addItemListener {
                passObjectScale = isSelected
                invokeLater { rebuildUi() }
              }
            }
        }
        indent {
          row {
            checkBox("Pass context component:")
              .selected(passContextContext)
              .applyToComponent {
                addItemListener {
                  passContextContext = isSelected
                  invokeLater { rebuildUi() }
                }
              }.enabled(passObjectScale)
          }
        }
        row {
          checkBox("Mark scalable:")
            .selected(markScalable)
            .applyToComponent {
              addItemListener {
                markScalable = isSelected
                invokeLater {
                  rebuildIcons()
                  rebuildUi()
                }
              }
            }
        }
        row {
          checkBox("Wrap with DeferredIcon:")
            .selected(wrapDeferred)
            .applyToComponent {
              addItemListener {
                wrapDeferred = isSelected
                invokeLater {
                  rebuildIcons()
                  rebuildUi()
                }
              }
            }
        }

        row("User scale:") {
          spinner(1.0..4.0, 0.25).applyToComponent {
            value = userScale.toDouble()
            addChangeListener {
              JBUIScale.setUserScaleFactor((value as Double).toFloat())
              invokeLater { rebuildUi() }
            }
          }
        }

        row("Object scale:") {
          spinner(1.0..4.0, 0.25).applyToComponent {
            value = this@IconScaleTestDialog.objectScale.toDouble()
            addChangeListener {
              this@IconScaleTestDialog.objectScale = (value as Double).toFloat()
              invokeLater { rebuildUi() }
            }
          }
        }
      }

      for ((name, icon) in icons) {
        addIconDescription(name, icon)
      }

      row {
        val list = JList(icons.values.toTypedArray())
        list.cellRenderer = SimpleListCellRenderer.create { label, icon, _ ->
          label.icon = icon
          label.text = "${icon.iconWidth}x${icon.iconHeight} - ${icon}"
        }
        scrollCell(list).align(Align.FILL)
      }.resizableRow()
    }
  }

  private fun Panel.addIconDescription(text: String, icon: Icon) {
    val label = JLabel(icon)
    label.border = SideBorder(JBColor.RED, SideBorder.ALL)
    row {
      cell(label)
        .comment("$text ${icon.iconWidth}x${icon.iconHeight} - ${icon}", maxLineLength = MAX_LINE_LENGTH_NO_WRAP)
    }
  }

  private fun buildIcon(@Suppress("SameParameterValue") size: Int, builder: () -> Icon): Icon {
    if (wrapDeferred) {
      val uniqueKey = Any()
      return IconManager.getInstance().createDeferredIcon(EmptyIcon.create(size), uniqueKey) { _ -> builder() }
    }
    return builder()
  }

  private fun prepareIcon(icon: Icon): Icon {
    if (passObjectScale) {
      if (passContextContext) {
        return IconUtil.scale(icon, rootPane, objectScale)
      }
      else {
        return IconUtil.scale(icon, null, objectScale)
      }
    }
    return icon
  }
}

private class PlainCircleIcon(val size: Int) : Icon {
  override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
    drawCircledIcon(g, x, y, iconWidth, iconHeight)
  }

  override fun getIconWidth(): Int = size

  override fun getIconHeight(): Int = size
}

private class ScaledCircleIcon(val size: Int) : JBScalableIcon() {
  override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
    drawCircledIcon(g, x, y, iconWidth, iconHeight)
  }

  override fun getIconWidth(): Int = ceil(scaleVal(size.toDouble())).toInt()

  override fun getIconHeight(): Int = ceil(scaleVal(size.toDouble())).toInt()
}

private class CachingCircleIcon(val size: Int) : JBCachingScalableIcon<CachingCircleIcon>() {
  override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
    drawCircledIcon(g, x, y, iconWidth, iconHeight)
  }

  override fun getIconWidth(): Int = ceil(scaleVal(size.toDouble())).toInt()

  override fun getIconHeight(): Int = ceil(scaleVal(size.toDouble())).toInt()

  override fun copy(): CachingCircleIcon = CachingCircleIcon(size).also {
    it.updateContextFrom(this) // <-- pay attention here
  }
}

private fun drawCircledIcon(g: Graphics, x: Int, y: Int, width: Int, height: Int) {
  val gg = g.create() as Graphics2D
  GraphicsUtil.setupAntialiasing(gg)
  GraphicsUtil.setupAAPainting(gg)

  gg.color = JBColor.foreground()
  gg.font = UIUtil.getFont(UIUtil.FontSize.MINI, null)
  UIUtil.drawCenteredString(gg, Rectangle(x, y, width, height), "$width")

  gg.color = JBColor.BLUE
  gg.drawOval(x, y, width, height)

  gg.dispose()
}