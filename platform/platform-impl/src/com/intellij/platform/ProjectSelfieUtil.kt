// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform

import com.intellij.ide.ui.UISettings
import com.intellij.openapi.application.appSystemDir
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.icons.readImage
import com.intellij.ui.icons.writeImage
import com.intellij.ui.scale.JBUIScale
import com.intellij.ui.scale.ScaleContext
import com.intellij.util.ui.ImageUtil
import java.awt.Component
import java.awt.GraphicsDevice
import java.awt.Image
import java.awt.image.BufferedImage
import java.nio.file.Path

internal object ProjectSelfieUtil {
  val isEnabled: Boolean
    get() = ExperimentalUI.isNewUI() && Registry.`is`("ide.project.loading.show.last.state", true)

  internal fun getSelfieLocation(projectWorkspaceId: String): Path {
    return appSystemDir.resolve("project-selfies-v2").resolve("$projectWorkspaceId.ij")
  }

  fun readProjectSelfie(value: String, device: GraphicsDevice): Image? {
    return readImage(getSelfieLocation(value), scaleContextProvider = {
      ScaleContext.create(device.defaultConfiguration)
    })
  }

  fun takeProjectSelfie(component: Component, selfieLocation: Path) {
    //val start = System.currentTimeMillis()
    val graphicsConfiguration = component.graphicsConfiguration
    val image = ImageUtil.createImage(graphicsConfiguration, component.width, component.height, BufferedImage.TYPE_INT_ARGB)
    UISettings.setupAntialiasing(image.graphics)

    component.paint(image.graphics)
    writeImage(file = selfieLocation, image = image, scale = JBUIScale.sysScale(graphicsConfiguration))

    //println("Write image: " + (System.currentTimeMillis() - start) + "ms")
  }
}