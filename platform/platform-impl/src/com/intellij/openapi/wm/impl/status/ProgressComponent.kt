// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.status

import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.ide.ui.UISettings.Companion.setupAntialiasing
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.impl.InternalUICustomization
import com.intellij.openapi.progress.ProgressModel
import com.intellij.openapi.progress.TaskInfo
import com.intellij.openapi.ui.popup.IconButton
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.ColorUtil
import com.intellij.ui.InplaceButton
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.ui.components.panels.Wrapper
import com.intellij.util.ui.GraphicsUtil
import com.intellij.util.ui.GridBag
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.StartupUiUtil.isUnderDarcula
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.ApiStatus
import java.awt.*
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.function.Consumer
import javax.swing.*

/**
 * This component was copy-pasted from `InlineProgressIndicator` with the following modification:
 * The inheritance from `ProgressIndicator` has been replaced by using `ProgressModel` as a model attribute.
 *
 * This serves as the core UI component for tracking and displaying progress based on a provided {@link ProgressModel}.
 */

@ApiStatus.Internal
open class ProgressComponent(val isCompact: Boolean, val info: TaskInfo, progressModel: ProgressModel) : Disposable {
  val textPanel: TextPanel
  protected val detailsPanel: TextPanel
  private val eastButtons: List<ProgressButton>

  val progress: JProgressBar

  val component: JPanel

  val indicatorModel: ProgressModel

  private val processName: TextPanel
  private var isDisposed = false

  init {
    progressModel.addOnChangeAction { queueProgressUpdate() }
    progressModel.addOnFinishAction { onFinish() }
    indicatorModel = progressModel

    progress = JProgressBar(SwingConstants.HORIZONTAL)
    progress.setOpaque(false)
    UIUtil.applyStyle(UIUtil.ComponentStyle.MINI, progress)

    textPanel = TextPanel()
    detailsPanel = TextPanel()
    processName = TextPanel()
    eastButtons = createEastButtons()
    component = createComponent()
  }

  protected open fun createComponent(): JPanel {
    val component = MyComponent(isCompact, processName)
    if (isCompact) {
      component.setLayout(BorderLayout(2, 0))
      createCompactTextAndProgress(component)
      component.add(createButtonPanel(
        eastButtons.map{ b: ProgressButton -> b.button }), BorderLayout.EAST)
      component.setToolTipText(indicatorModel.title + ". " + IdeBundle.message("progress.text.clickToViewProgressWindow"))
    }
    else {
      component.setLayout(BorderLayout())
      processName.text = indicatorModel.title
      component.add(processName, BorderLayout.NORTH)
      processName.setForeground(UIUtil.getPanelBackground().brighter().brighter())
      processName.setBorder(JBUI.Borders.empty(2))

      val content = NonOpaquePanel(BorderLayout())
      content.setBorder(JBUI.Borders.empty(2, 2, 2, if (indicatorModel.isCancellable()) 2 else 4))
      component.add(content, BorderLayout.CENTER)

      content.add(createButtonPanel(
        eastButtons.map{ b: ProgressButton -> withBorder(b.button) }),
                  BorderLayout.EAST)
      content.add(textPanel, BorderLayout.NORTH)
      content.add(progress, BorderLayout.CENTER)
      content.add(detailsPanel, BorderLayout.SOUTH)

      component.setBorder(JBUI.Borders.empty(2))
    }
    UIUtil.uiTraverser(component).forEach(Consumer { o: Component? -> (o as JComponent).setOpaque(false) })

    if (!isCompact) {
      processName.recomputeSize()
      textPanel.recomputeSize()
      detailsPanel.recomputeSize()
    }
    return component
  }

  protected open fun onFinish() {
  }

  protected open fun createCompactTextAndProgress(component: JPanel) {
    val textAndProgress: JPanel = NonOpaquePanel(BorderLayout())
    textAndProgress.add(textPanel, BorderLayout.CENTER)

    val progressWrapper = wrapProgress()
    progressWrapper.setBorder(JBUI.Borders.empty(0, 4))

    textAndProgress.add(progressWrapper, BorderLayout.EAST)
    component.add(textAndProgress, BorderLayout.CENTER)
  }

  protected open fun wrapProgress(): JComponent {
    val progressWrapper = NonOpaquePanel(BorderLayout())
    progressWrapper.add(progress, BorderLayout.CENTER)
    return progressWrapper
  }

  protected open fun createEastButtons(): List<ProgressButton> {
    return listOf(createCancelButton())
  }

  protected fun createCancelButton(): ProgressButton {
    val cancelButton = InplaceButton(
      IconButton(indicatorModel.getCancelTooltipText(),
                 if (isCompact) AllIcons.Process.StopSmall else AllIcons.Process.Stop,
                 if (isCompact) AllIcons.Process.StopSmallHovered else AllIcons.Process.StopHovered),
      ActionListener { _: ActionEvent? -> cancelRequest() }).setFillBg(false)

    cancelButton.isVisible = indicatorModel.isCancellable()

    return ProgressButton(cancelButton, Runnable { cancelButton.setPainting(!this.isStopping) })
  }

  protected open fun cancelRequest() {
    indicatorModel.cancel()
  }

  open fun getText(): @NlsContexts.ProgressText String? {
    return indicatorModel.getText()
  }

  fun getText2(): @NlsContexts.ProgressDetails String? {
    return indicatorModel.getDetails()
  }

  val fraction: Double
    get() = indicatorModel.getFraction()

  val isIndeterminate: Boolean
    get() = indicatorModel.isIndeterminate()

  fun updateAndRepaint() {
    if (this.isDisposed) {
      return
    }

    updateProgressNow()

    component.repaint()
  }

  open fun updateProgressNow() {
    if (this.isPaintingIndeterminate) {
      progress.setIndeterminate(true)
    }
    else {
      progress.setIndeterminate(false)
      progress.minimum = 0
      progress.maximum = 100
    }
    if (fraction > 0) {
      progress.setValue((fraction * 99 + 1).toInt())
    }

    val oldTextValue = this.textValue
    val text = indicatorModel.getText()
    val text2 = indicatorModel.getDetails()
    this.textValue = text ?: ""
    this.text2Value = text2 ?: ""

    if (isCompact && StringUtil.isEmpty(this.textValue)) {
      this.textValue = indicatorModel.title
    }

    IntegrationTestsProgressesTracker.progressTitleChanged(indicatorModel, oldTextValue?: "", this.textValue?: "")

    if (this.isStopping) {
      if (isCompact) {
        this.textValue = IdeBundle.message("progress.text.stopping", this.textValue)
      }
      else {
        this.processNameValue = IdeBundle.message("progress.text.stopping", indicatorModel.title)
        setTextEnabled(false)
        setText2Enabled(false)
      }
      progress.setEnabled(false)
    }
    else {
      setTextEnabled(true)
      setText2Enabled(true)
      progress.setEnabled(true)
    }

    for (button in eastButtons) {
      button.updateAction.run()
    }
  }

  protected open var textValue: @NlsContexts.DetailedDescription String?
    get() = textPanel.text
    set(text) {
      this.textPanel.text = text
    }

  protected open fun setTextEnabled(value: Boolean) {
    textPanel.setEnabled(value)
  }

  protected open var text2Value: @NlsContexts.DetailedDescription String?
    get() = detailsPanel.text
    set(text) {
      detailsPanel.text = text
    }

  protected open fun setText2Enabled(value: Boolean) {
    detailsPanel.setEnabled(value)
  }

  protected open var processNameValue: @NlsContexts.ProgressTitle String?
    get() = processName.text
    set(text) {
      processName.text = text
    }

  protected val isPaintingIndeterminate: Boolean
    get() = indicatorModel.isIndeterminate() || fraction == 0.0

  protected val isStopping: Boolean
    get() = indicatorModel.isStopping(info)

  protected open val isFinished: Boolean
    get() = false

  open fun queueProgressUpdate() {
    updateAndRepaint()
  }

  private inner class MyComponent(private val isCompact: Boolean, private val myProcessName: JComponent) : JPanel() {
    init {
      addMouseListener(object : MouseAdapter() {
        override fun mousePressed(e: MouseEvent) {
          if (UIUtil.isCloseClick(e) && bounds.contains(e.getX(), e.getY())) {
            cancelRequest()
          }
        }
      })
    }

    override fun paint(g: Graphics) {
      super.paint(InternalUICustomization.getInstance()?.preserveGraphics(g))
    }

    override fun paintComponent(g: Graphics) {
      if (isCompact) {
        super.paintComponent(g)
        return
      }

      val c = GraphicsUtil.setupAAPainting(g)
      setupAntialiasing(g)

      val arc = 8
      var bg = getBackground()
      val bounds = myProcessName.bounds
      val label = SwingUtilities.convertRectangle(myProcessName.getParent(), bounds, this)

      g.color = UIUtil.getPanelBackground()
      g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc)

      if (!isUnderDarcula) {
        bg = ColorUtil.toAlpha(bg.darker().darker(), 230)
        g.color = bg

        g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc)

        g.color = UIUtil.getPanelBackground()
        g.fillRoundRect(0, getHeight() / 2, getWidth() - 1, getHeight() / 2, arc, arc)
        g.fillRect(0, label.maxY.toInt() + 1, getWidth() - 1, getHeight() / 2)
      }
      else {
        bg = bg.brighter()
        g.color = bg
        g.drawLine(0, label.maxY.toInt() + 1, getWidth() - 1, label.maxY.toInt() + 1)
      }

      g.color = bg
      g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc)

      c.restore()
    }
  }

  override fun dispose() {
    isDisposed = true
  }

  class ProgressButton(@JvmField val button: InplaceButton, @JvmField val updateAction: Runnable)
  companion object {
    @JvmStatic
    fun createButtonPanel(components: Iterable<JComponent>): JPanel {
      val iconsPanel: JPanel = NonOpaquePanel(GridBagLayout())
      val gb = GridBag().setDefaultFill(GridBagConstraints.BOTH)
      for (component in components) {
        iconsPanel.add(component, gb.next().insets(0, 0, 0, 2))
      }
      return iconsPanel
    }

    private fun withBorder(button: InplaceButton): Wrapper {
      val wrapper = Wrapper(button)
      wrapper.setBorder(JBUI.Borders.empty(0, 3, 0, 2))
      return wrapper
    }
  }
}