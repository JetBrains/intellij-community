// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.ui

import com.intellij.CommonBundle
import com.intellij.openapi.application.EDT
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.impl.fadeOut
import com.intellij.util.ui.AsyncProcessIcon
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.StartupUiUtil
import kotlinx.coroutines.*
import java.awt.*
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants
import kotlin.time.Duration

internal class AsyncLoadingDecorator(private val startDelay: Duration) {
  // access only from EDT
  private var loadingLayer: LoadingLayer? = null

  init {
    require(startDelay >= Duration.ZERO)
  }

  fun startLoading(scope: CoroutineScope, addUi: (component: JComponent) -> Unit): Job {
    val scheduleTime = System.currentTimeMillis()
    return scope.launch {
      delay((startDelay.inWholeMilliseconds - (System.currentTimeMillis() - scheduleTime)).coerceAtLeast(0))
      withContext(Dispatchers.EDT) {
        val loadingLayer = LoadingLayer(AsyncProcessIcon.Big("Loading"))
        addUi(loadingLayer)
        this@AsyncLoadingDecorator.loadingLayer = loadingLayer
      }
    }
  }

  fun stopLoading(scope: CoroutineScope, indicatorJob: Job) {
    // no need to join - executed in EDT
    indicatorJob.cancel()
    scope.launch(Dispatchers.EDT) {
      val loadingLayer = loadingLayer ?: return@launch
      try {
        loadingLayer.processIcon.suspend()

        fadeOut(painter = loadingLayer::setAlpha)

        val parent = loadingLayer.parent
        parent.remove(loadingLayer)
        parent.repaint()
      }
      finally {
        Disposer.dispose(loadingLayer.processIcon)
      }
    }
  }

  private class LoadingLayer(@JvmField val processIcon: AsyncProcessIcon) : JPanel(GridBagLayout()) {
    private var currentAlpha = -1f

    init {
      border = JBUI.Borders.empty(10)
      isOpaque = false

      val text = CommonBundle.getLoadingTreeNodeText()
      val textComponent = JLabel(text, SwingConstants.CENTER)
      textComponent.font = StartupUiUtil.labelFont
      textComponent.foreground = JBUI.CurrentTheme.ContextHelp.FOREGROUND

      processIcon.resume()
      add(processIcon, GridBagConstraints().also {
        it.gridx = 0
        it.gridy = 0
        it.anchor = GridBagConstraints.CENTER
        if (text.endsWith("...")) {
          it.insets = JBUI.insetsRight(8)
        }
      })

      add(textComponent, GridBagConstraints().also {
        it.gridx = 0
        it.gridy = 1
        it.anchor = GridBagConstraints.CENTER
        it.insets = JBUI.insetsTop(6)
      })
    }

    fun setAlpha(alpha: Float) {
      currentAlpha = alpha

      paintImmediately(bounds)
    }

    override fun paintChildren(g: Graphics) {
      if (currentAlpha != -1f) {
        (g as Graphics2D).composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, currentAlpha)
      }
      super.paintChildren(g)
    }
  }
}