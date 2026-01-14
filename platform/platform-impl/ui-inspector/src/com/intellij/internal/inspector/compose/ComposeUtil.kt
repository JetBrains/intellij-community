// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.inspector.compose

import com.intellij.openapi.diagnostic.thisLogger
import java.awt.Component
import java.awt.Container
import java.awt.Window

internal object ComposeUtil {
    private val logger = thisLogger()

    fun findComposePanels(): List<Container> {
        val result = mutableListOf<Container>()
        try {
            Window.getWindows().forEach { win -> walkGenericAWT(win, result) }
        } catch (_: Exception) {}
        return result
    }

    private fun walkGenericAWT(comp: Component, list: MutableList<Container>) {
        val name = comp.javaClass.name
        val isComposePanel = "ComposePanel" in name && "JewelComposePanel" !in name
        val isComposeWindow = "ComposeWindow" in name
        if ((isComposePanel || isComposeWindow) && comp is Container) {
            list.add(comp)
        }
        if (comp is Container) {
            try {
                comp.components.forEach { walkGenericAWT(it, list) }
            } catch (_: Exception) {}
        }
    }

    fun Container.onEachRootNode(action: (Any) -> Unit) {
        val composeContainer = ReflectHelper.getFieldValue(this, "_composeContainer")
        val mediator = ReflectHelper.getFieldValue(composeContainer!!, "mediator")
        val sceneDelegate = ReflectHelper.getFieldValue(mediator!!, $$"scene$delegate")
        val scene = (sceneDelegate as Lazy<*>).value

        if (scene == null) {
            logger.warn("Could not find internal 'scene'.")
            return
        }

        val rootNode = findRootLayoutNode(scene)
        if (rootNode == null) {
            logger.warn("Could not find generic root LayoutNode.")
            return
        }

        action(rootNode)
    }

    private fun findRootLayoutNode(scene: Any): Any? {
        ReflectHelper.getFieldValue(scene, "root")?.let {
            return it
        }
        val mainOwner = ReflectHelper.getFieldValue(scene, "mainOwner")
        if (mainOwner != null) {
            ReflectHelper.getFieldValue(mainOwner, "root")?.let {
                return it
            }
            val owner = ReflectHelper.getFieldValue(mainOwner, "_owner")
            return ReflectHelper.getFieldValue(owner!!, "root")
        }
        return null
    }
}
