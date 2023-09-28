/*
 * Copyright 2000-2016 Nikolay Chashnikov.
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

/**
 * @author nik
 */
package org.nik.presentationAssistant

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.IdeFrame
import com.intellij.openapi.wm.WindowManager
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.util.Alarm
import com.intellij.util.ui.Animator
import com.intellij.util.ui.UIUtil
import java.awt.*
import javax.swing.*

class ActionInfoPanel(project: Project, textFragments: List<Pair<String, Font?>>) : NonOpaquePanel(BorderLayout()), Disposable {
    private val hint: JBPopup
    private val labelsPanel: JPanel
    private val hideAlarm = Alarm(this)
    private var animator: Animator
    private var phase = Phase.FADING_IN
    private val hintAlpha = if (UIUtil.isUnderDarcula()) 0.05.toFloat() else 0.1.toFloat()
    private val pluginConfiguration = getPresentationAssistant().configuration
    enum class Phase { FADING_IN, SHOWN, FADING_OUT, HIDDEN}
    init
    {
        labelsPanel = NonOpaquePanel(FlowLayout(FlowLayout.CENTER, 0, 0))
        updateLabelText(project, textFragments)
        background = EditorColorsManager.getInstance().globalScheme.getColor(BACKGROUND_COLOR_KEY)
        isOpaque = true
        add(labelsPanel, BorderLayout.CENTER)
        val emptyBorder = BorderFactory.createEmptyBorder(5, 10, 5, 10)
        border = emptyBorder


        hint = with(JBPopupFactory.getInstance().createComponentPopupBuilder(this, this)) {
            setAlpha(1.0.toFloat())
            setFocusable(false)
            setBelongsToGlobalPopupStack(false)
            setCancelKeyEnabled(false)
            setCancelCallback { phase = Phase.HIDDEN; true }
            createPopup()
        }
        hint.addListener(object : JBPopupListener {
            override fun beforeShown(lightweightWindowEvent: LightweightWindowEvent) {}
            override fun onClosed(lightweightWindowEvent: LightweightWindowEvent) {
                phase = Phase.HIDDEN
            }
        })

        animator = FadeInOutAnimator(true)
        hint.show(computeLocation(project))
        animator.resume()
    }


    private fun fadeOut() {
        if (phase != Phase.SHOWN) return
        phase = Phase.FADING_OUT
        Disposer.dispose(animator)
        animator = FadeInOutAnimator(false)
        animator.resume()
    }

    inner class FadeInOutAnimator(private val forward: Boolean): Animator("Action Hint Fade In/Out", 5, 100, false, forward) {
        override fun paintNow(frame: Int, totalFrames: Int, cycle: Int) {
            if (forward && phase != Phase.FADING_IN
                || !forward && phase != Phase.FADING_OUT) return
            setAlpha(hintAlpha +(1- hintAlpha)*(totalFrames-frame)/totalFrames)
        }

        override fun paintCycleEnd() {
            if (forward) {
                showFinal()
            }
            else {
                close()
            }
        }
    }

    private fun getHintWindow(): Window? {
        if (hint.isDisposed) return null
        val window = SwingUtilities.windowForComponent(hint.content)
        if (window != null && window.isShowing) return window
        return null
    }

    private fun setAlpha(alpha: Float) {
        val window = getHintWindow()
        if (window != null) {
            WindowManager.getInstance().setAlphaModeRatio(window, alpha)
        }
    }

    private fun showFinal() {
        phase = Phase.SHOWN
        setAlpha(hintAlpha)
        hideAlarm.cancelAllRequests()
        hideAlarm.addRequest({ fadeOut() }, pluginConfiguration.hideDelay, ModalityState.any())
    }

    fun updateText(project: Project, textFragments: List<Pair<String, Font?>>) {
        if (getHintWindow() == null) return
        labelsPanel.removeAll()
        updateLabelText(project, textFragments)
        hint.setLocation(computeLocation(project).screenPoint)
        hint.size = preferredSize
        hint.content.validate()
        hint.content.repaint()
        showFinal()
    }

    private fun computeLocation(project: Project): RelativePoint {
        val ideFrame = WindowManager.getInstance().getIdeFrame(project)!!
        val statusBarHeight = ideFrame.statusBar?.component?.height ?: 0
        val visibleRect = ideFrame.component.visibleRect
        val popupSize = preferredSize
        val x = when (pluginConfiguration.horizontalAlignment) {
            PopupHorizontalAlignment.LEFT -> visibleRect.x + pluginConfiguration.margin
            PopupHorizontalAlignment.CENTER -> visibleRect.x + (visibleRect.width - popupSize.width) / 2
            PopupHorizontalAlignment.RIGHT -> visibleRect.x + visibleRect.width - popupSize.width - pluginConfiguration.margin
        }
        val y = when (pluginConfiguration.verticalAlignment) {
            PopupVerticalAlignment.TOP -> visibleRect.y + pluginConfiguration.margin
            PopupVerticalAlignment.BOTTOM -> visibleRect.y + visibleRect.height - popupSize.height - statusBarHeight - pluginConfiguration.margin
        }
        return RelativePoint(ideFrame.component, Point(x, y))
    }

    private fun updateLabelText(project: Project, textFragments: List<Pair<String, Font?>>) {
        val ideFrame = WindowManager.getInstance().getIdeFrame(project)!!
        for (label in createLabels(textFragments, ideFrame)) {
            labelsPanel.add(label)
        }
    }

    private fun List<Pair<String, Font?>>.mergeFragments() : List<Pair<String, Font?>> {
        val result = ArrayList<Pair<String, Font?>>()
        for (item in this) {
            val last = result.lastOrNull()
            if (last != null && last.second == item.second) {
                result.removeAt(result.lastIndex)
                result.add(Pair(last.first + item.first, last.second))
            }
            else {
                result.add(item)
            }
        }
        return result
    }

    private fun createLabels(textFragments: List<Pair<String, Font?>>, ideFrame: IdeFrame): List<JLabel> {
        var fontSize = pluginConfiguration.fontSize.toFloat()
        val color = EditorColorsManager.getInstance().globalScheme.getColor(FOREGROUND_COLOR_KEY)
        val labels = textFragments.mergeFragments().map {
            val label = JLabel("<html>${it.first}</html>", SwingConstants.CENTER)
            label.foreground = color
            if (it.second != null) label.font = it.second
            label
        }
        fun setFontSize(size: Float) {
            for (label in labels) {
                label.font = label.font.deriveFont(size)
            }
            val maxAscent = labels.maxOfOrNull { it.getFontMetrics(it.font).maxAscent } ?: 0
            for (label in labels) {
                val ascent = label.getFontMetrics(label.font).maxAscent
                if (ascent < maxAscent) {
                    label.border = BorderFactory.createEmptyBorder(maxAscent - ascent, 0, 0, 0)
                }
                else {
                    label.border = null
                }
            }
        }
        setFontSize(fontSize)
        val frameWidth = ideFrame.component.width
        if (frameWidth > 100) {
            while (labels.sumOf { it.preferredSize.width } > frameWidth - 10 && fontSize > 12) {
                setFontSize(--fontSize)
            }
        }
        return labels
    }

    fun close() {
        Disposer.dispose(this)
    }

    override fun dispose() {
        phase = Phase.HIDDEN
        if (!hint.isDisposed) {
            hint.cancel()
        }
        Disposer.dispose(animator)
    }

    fun canBeReused(): Boolean = phase == Phase.FADING_IN || phase == Phase.SHOWN
}
