// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui

import com.intellij.ide.DataManager
import com.intellij.ide.IdeBundle
import com.intellij.notification.Notification
import com.intellij.notification.impl.NotificationsManagerImpl
import com.intellij.notification.impl.NotificationsToolWindowFactory
import com.intellij.notification.impl.ui.NotificationsUtil
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.ui.components.labels.LinkListener
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.Nls
import java.awt.*
import java.util.*
import javax.swing.JLayeredPane
import javax.swing.JPanel
import javax.swing.JRootPane
import javax.swing.SwingConstants

/**
 * @author Alexander Lobas
 */
class ActionCenterBalloonLayout(parent: JRootPane, insets: Insets) : BalloonLayoutImpl(parent, insets) {
  private val myCollapsedData = HashMap<Balloon, CollapseInfo>()

  override fun dispose() {
    super.dispose()
    myCollapsedData.clear()
  }

  override fun add(newBalloon: Balloon, layoutData: Any?) {
    ApplicationManager.getApplication().assertIsDispatchThread()

    if (layoutData is BalloonLayoutData) {
      if (layoutData.collapseType == null) {
        throw NullPointerException()
      }
      var size = myBalloons.size
      for (balloon in myBalloons) {
        if (myLayoutData[balloon] == null) {
          size--
          break
        }
      }
      if (size < 3) {
        addNewBalloon(newBalloon, layoutData)
      }
      else {
        doCollapse(newBalloon, layoutData)
      }
    }
    else {
      for (balloon in myBalloons) {
        if (myLayoutData[balloon] == null) {
          remove(balloon)
          break
        }
      }
      addNewBalloon(newBalloon, null)
    }
  }

  private fun addNewBalloon(balloon: Balloon, layoutData: Any?, callback: (() -> Unit)? = null) {
    myBalloons.add(balloon)

    if (layoutData is BalloonLayoutData) {
      layoutData.closeAll = myCloseAll
      layoutData.doLayout = myLayoutRunnable
      myLayoutData[balloon] = layoutData
    }
    Disposer.register(balloon) {
      remove(balloon, false)
      queueRelayout()
    }

    calculateSize()
    relayout()
    if (!balloon.isDisposed) {
      balloon.show(myLayeredPane)
    }
    callback?.invoke()
    fireRelayout()
  }

  private fun doCollapse(newBalloon: Balloon, newLayoutData: BalloonLayoutData) {
    val exist = Array(1) { false }
    val info = doCollapseForNewBalloon(newBalloon, newLayoutData, exist) ?: doCollapseForExistBalloon(newLayoutData, exist[0])

    info.addBalloon()

    addNewBalloon(newBalloon, newLayoutData) {
      info.show(myLayeredPane)
    }
  }

  private fun doCollapseForNewBalloon(newBalloon: Balloon, newLayoutData: BalloonLayoutData, useExist: Array<Boolean>): CollapseInfo? {
    var count = 0
    for (balloon in myBalloons) {
      if (myLayoutData[balloon]?.collapseType === newLayoutData.collapseType) {
        count++
        if (count > 1) {
          useExist[0] = true
          return null
        }
      }
    }

    for (balloon in myBalloons) {
      if (myLayoutData[balloon]?.collapseType === newLayoutData.collapseType) {
        return doCollapseForBalloons(balloon, newBalloon, newLayoutData)
      }
    }
    return null
  }

  private fun doCollapseForExistBalloon(newLayoutData: BalloonLayoutData, useExist: Boolean): CollapseInfo {
    val size = myBalloons.size
    for (i in 0 until size) {
      val balloon = myBalloons[i]
      val layoutData = myLayoutData[balloon]

      if (layoutData != null && (layoutData.collapseType === newLayoutData.collapseType) == useExist) {
        for (j in i + 1 until size) {
          val nextBalloon = myBalloons[j]
          val nextLayoutData = myLayoutData[nextBalloon]

          if (nextLayoutData != null && layoutData.collapseType === nextLayoutData.collapseType) {
            return doCollapseForBalloons(balloon, nextBalloon, newLayoutData)
          }
        }
      }
    }
    throw IllegalStateException()
  }

  private fun doCollapseForBalloons(oldBalloon: Balloon, newBalloon: Balloon, newLayoutData: BalloonLayoutData): CollapseInfo {
    val info = myCollapsedData[oldBalloon]
    if (info == null) {
      remove(oldBalloon)
      return createCollapsedData(newBalloon, newLayoutData)
    }

    myCollapsedData.remove(oldBalloon)
    remove(oldBalloon)

    myCollapsedData[newBalloon] = info

    return info
  }

  private fun createCollapsedData(balloon: Balloon, newLayoutData: BalloonLayoutData): CollapseInfo {
    val titleEnd = IdeBundle.message(if (newLayoutData.collapseType === BalloonLayoutData.Type.Timeline)
      "notifications.collapse.balloon.title.timeline"
    else "notifications.collapse.balloon.title.suggestion")

    val newCollapseInfo = CollapseInfo(titleEnd)
    myCollapsedData[balloon] = newCollapseInfo

    return newCollapseInfo
  }

  override fun remove(balloon: Balloon, hide: Boolean) {
    myCollapsedData.remove(balloon)?.hide()
    super.remove(balloon, hide)
  }

  override fun preMerge(notification: Notification): BalloonLayoutData.MergeInfo? = null

  override fun calculateSize() {
    super.calculateSize()

    for (balloon in myBalloons) {
      myCollapsedData[balloon]?.calculateSize()
    }
  }

  override fun getSize(balloon: Balloon): Dimension {
    val size = super.getSize(balloon)
    val info = myCollapsedData[balloon]
    if (info != null) {
      size.height += info.height
    }
    return size
  }

  override fun setBounds(balloons: MutableList<Balloon>, startX: Int, startY: Int) {
    var y = startY

    for (balloon in balloons) {
      val bounds = Rectangle(super.getSize(balloon))
      val info = myCollapsedData[balloon]
      if (info != null) {
        info.balloon.setBounds(Rectangle(startX - bounds.width, y - info.fullHeight, bounds.width, info.fullHeight))
        y -= info.height
      }

      y -= bounds.height
      bounds.setLocation(startX - bounds.width, y)
      balloon.setBounds(bounds)
    }
  }

  private inner class CollapseInfo(@Nls val titleEnd: String) {
    var collapsedBalloons = 0
    val titleLabel: LinkLabel<Any>
    val balloon: Balloon
    var doShow = true
    var height = 0
    var fullHeight = 0

    init {
      titleLabel = object : LinkLabel<Any>() {
        override fun isInClickableArea(pt: Point): Boolean {
          return true
        }

        override fun getTextColor(): Color = NotificationsUtil.getMoreButtonForeground()
      }
      titleLabel.setPaintUnderline(false)
      titleLabel.font = JBFont.medium()
      titleLabel.horizontalAlignment = SwingConstants.CENTER
      titleLabel.border = JBUI.Borders.empty(10, 0, 4, 0)
      titleLabel.icon = null

      titleLabel.setListener(LinkListener { _, _ ->
        val project = CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(titleLabel))
        if (project != null) {
          closeAll()
          ToolWindowManager.getInstance(project).getToolWindow(NotificationsToolWindowFactory.ID)?.show()
        }
      }, null)

      val panel = JPanel(BorderLayout())
      panel.isOpaque = false
      panel.add(titleLabel)

      val builder = JBPopupFactory.getInstance().createBalloonBuilder(panel)
      builder.setFillColor(NotificationsUtil.getMoreButtonBackground())
        .setCloseButtonEnabled(true)
        .setShowCallout(false)
        .setShadow(false)
        .setAnimationCycle(0)
        .setHideOnClickOutside(false)
        .setHideOnAction(false)
        .setHideOnKeyOutside(false)
        .setHideOnFrameResize(false)
        .setBorderColor(NotificationsManagerImpl.BORDER_COLOR)
        .setBorderInsets(JBInsets.emptyInsets())

      balloon = builder.createBalloon()

      if (balloon is BalloonImpl) {
        balloon.setAnimationEnabled(false)
        balloon.setZeroPositionInLayer(false)

        balloon.setShadowBorderProvider(
          NotificationBalloonRoundShadowBorderProvider(NotificationsUtil.getMoreButtonBackground(), NotificationsManagerImpl.BORDER_COLOR))

        balloon.setActionProvider(object : BalloonImpl.ActionProvider {
          override fun createActions(): List<BalloonImpl.ActionButton> {
            return Collections.emptyList()
          }

          override fun layout(bounds: Rectangle) {
          }
        })
      }
    }

    fun addBalloon() {
      collapsedBalloons++
      titleLabel.text = IdeBundle.message("notifications.collapse.balloon.title", collapsedBalloons, titleEnd)
    }

    fun show(pane: JLayeredPane) {
      if (doShow) {
        doShow = false
        balloon.show(pane)
      }
    }

    fun hide() {
      balloon.hide()
    }

    fun calculateSize() {
      val insets = (balloon as BalloonImpl).shadowBorderInsets
      fullHeight = balloon.preferredSize.height
      height = fullHeight - JBUI.scale(7) - insets.top - insets.bottom
    }
  }
}