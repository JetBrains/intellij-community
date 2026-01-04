// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.inspector.compose

import com.intellij.ide.lightEdit.LightEditCompatible
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import java.awt.AWTEvent
import java.awt.Toolkit
import java.awt.event.AWTEventListener
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class ToggleComposeLayoutBoundsAction : DumbAwareAction(), LightEditCompatible {
    private var listener: AWTEventListener? = null
    private val showLayoutBounds
        get() = listener != null

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun actionPerformed(e: AnActionEvent) {
        if (!showLayoutBounds) {
            listener = LayoutBoundsToggler.createAwtListener { LayoutBoundsToggler.setShowLayoutBoundsEnabled(true) }
            Toolkit.getDefaultToolkit().addAWTEventListener(listener, AWTEvent.HIERARCHY_EVENT_MASK)
            LayoutBoundsToggler.setShowLayoutBoundsEnabled(true)
        } else {
            Toolkit.getDefaultToolkit().removeAWTEventListener(listener)
            listener = null
            LayoutBoundsToggler.setShowLayoutBoundsEnabled(false)
        }
    }

    override fun update(e: AnActionEvent) {
        e.presentation.text = if (!showLayoutBounds) "Show Compose Layout Bounds" else "Hide Compose Layout Bounds"
    }
}
