// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.toolWindow.xNext.toolbar.actions.toolbar

import com.intellij.ide.dnd.*
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.fileEditor.impl.DesignProcessor
import com.intellij.toolWindow.xNext.toolbar.actions.XNextToolbarGroup
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.JBUI.CurrentTheme.Toolbar.mainToolbarButtonInsets
import java.awt.Color
import java.awt.Point

internal class XNextActionToolbar : ActionToolbarImpl ("XNextStatusBar", XNextToolbarGroup(), true){
  private val dndManager = DnDManager.getInstance()

  init {
    setCustomButtonLook(XNextToolWindowButtonLook())
    setMinimumButtonSize { ActionToolbar.experimentalToolbarMinimumButtonSize() }
    setActionButtonBorder(JBUI.Borders.empty(mainToolbarButtonInsets()))

    /*  MouseDragHelper.setComponentDraggable(this, true)

 dndManager.registerTarget(XNextDnDTarget(), this)
   dndManager.registerSource(XNextDnDSource(), this)*/

  }
  override fun getBackground(): Color? {
    return DesignProcessor.getInstance().getCustomMainBackgroundColor() ?: super.getBackground()
  }

/*  override fun createIconButton(action: AnAction, place: String, presentation: Presentation, minimumSize: Supplier<out Dimension>?): ActionButton {
    val button = super.createIconButton(action, place, presentation, minimumSize)

    MouseDragHelper.setComponentDraggable(button, true)

    return button
  }*/

  inner class XNextDnDSource : DnDSource {
    override fun canStartDragging(action: DnDAction?, dragOrigin: Point): Boolean {

      val componentAt = this@XNextActionToolbar.component.getComponentAt(dragOrigin)
      return true
      //TODO("Not yet implemented")
    }

    override fun startDragging(action: DnDAction?, dragOrigin: Point): DnDDragStartBean? {
      TODO("Not yet implemented")
    }
  }

  private class XNextDnDTarget : DnDTarget {
    override fun drop(event: DnDEvent?) {
      TODO("Not yet implemented")
    }

    override fun update(event: DnDEvent?): Boolean {
      TODO("Not yet implemented")
    }

  }

}
