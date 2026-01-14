// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.inspector.compose

import com.intellij.internal.inspector.compose.ComposeUtil.onEachRootNode
import com.intellij.openapi.diagnostic.thisLogger
import java.awt.Component
import java.awt.event.AWTEventListener
import java.awt.event.HierarchyEvent
import javax.swing.JPanel
import javax.swing.SwingUtilities

internal object LayoutBoundsToggler {
    private val logger = thisLogger()

    fun setShowLayoutBoundsEnabled(enabled: Boolean) {
        for (panel in ComposeUtil.findComposePanels()) {
            try {
                panel.onEachRootNode {
                    val owner = ReflectHelper.getFieldValue(it, "owner")
                    ReflectHelper.setFieldValue(owner!!, "showLayoutBounds", enabled)
                    invalidate(owner)
                }
                panel.revalidate()
                SwingUtilities.invokeLater { panel.repaint() }
            } catch (t: Throwable) {
                logger.error("Could not set layout bounds flag for panel $panel", t)
            }
        }
    }

    private fun invalidate(owner: Any) {
        if (owner.javaClass.name != "androidx.compose.ui.node.RootNodeOwner") {
            logger.info("${owner} is not a RootNodeOwner, can't invalidate")
            return
        }

        try {
            val ownerLayerManager = ReflectHelper.getFieldValue(owner, "ownedLayerManager")
            ReflectHelper.callMethod(ownerLayerManager!!, "invalidate")
        } catch (t: Throwable) {
            logger.error("Could not invoke invalidate layer manager for root node owner $owner", t)
        }
    }

    fun createAwtListener(onAttach: (Component) -> Unit): AWTEventListener = AWTEventListener { event ->
        // We only care about HierarchyEvents
        if (event !is HierarchyEvent) return@AWTEventListener

        // Filter for PARENT_CHANGED events (attachment/detachment)
        if ((event.getChangeFlags() and HierarchyEvent.PARENT_CHANGED.toLong()) != 0L) {
            val component: Component = event.component

            // Filter specifically for JPanels (or any other criteria)
            // If parent is not null, it was just attached.
            if (component is JPanel && component.getParent() != null) {
                onAttach(component)
            }
        }
    }
}
