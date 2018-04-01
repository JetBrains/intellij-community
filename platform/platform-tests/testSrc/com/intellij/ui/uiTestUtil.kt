// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui

import com.intellij.ide.ui.laf.IntelliJLaf
import com.intellij.ide.ui.laf.darcula.DarculaLaf
import com.intellij.openapi.application.invokeAndWaitIfNeed
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.ui.layout.*
import com.intellij.util.io.exists
import com.intellij.util.io.outputStream
import com.intellij.util.io.sanitizeFileName
import com.intellij.util.io.write
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.UIUtil
import io.netty.util.internal.SystemPropertyUtil
import net.miginfocom.swing.MigLayout
import org.assertj.core.data.Offset
import org.assertj.swing.assertions.Assertions
import org.junit.rules.ExternalResource
import org.junit.rules.TestName
import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.Yaml
import java.awt.*
import java.awt.image.BufferedImage
import java.nio.file.Path
import java.nio.file.Paths
import javax.imageio.ImageIO
import javax.swing.JFrame
import javax.swing.UIManager
import javax.swing.plaf.metal.MetalLookAndFeel
import kotlin.properties.Delegates

private val isUpdateSnapshotsGlobal by lazy { SystemPropertyUtil.getBoolean("test.update.snapshots", false) }

fun changeLafIfNeed(lafName: String) {
  if (UIManager.getLookAndFeel().name == lafName) {
    return
  }

  invokeAndWaitIfNeed {
    UIManager.setLookAndFeel(MetalLookAndFeel())
    val laf = if (lafName == "IntelliJ") IntelliJLaf() else DarculaLaf()
    UIManager.setLookAndFeel(laf)

    if (lafName == "Darcula") {
      // static init it is hell - UIUtil static init is called too early, so, call it to init properly
      // (otherwise null stylesheet added and it leads to NPE on set comment text)
      UIManager.getDefaults().put("javax.swing.JLabel.userStyleSheet", UIUtil.JBHtmlEditorKit.createStyleSheet())
    }
  }
}

class FrameRule : ExternalResource() {
  var frame: JFrame by Delegates.notNull()
    private set

  private var wasFrameCreated = false

  override fun after() {
    if (wasFrameCreated) {
      invokeAndWaitIfNeed {
        frame.isVisible = false
        frame.dispose()
      }
    }
  }

  // must be called in EDT
  fun show(component: Component, minSize: Dimension? = JBDimension(480, 320)) {
    wasFrameCreated = true
    frame = createTestFrame(minSize)
    frame.contentPane.add(component, BorderLayout.CENTER)

    frame.pack()
    frame.isVisible = true

    // clear focus from first input field
    frame.requestFocusInWindow()
  }
}

private fun createTestFrame(minSize: Dimension?): JFrame {
  val frame = JFrame()
  frame.isUndecorated = true
  if (minSize != null) {
    frame.minimumSize = minSize
  }

  val screenDevices = GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices
  if (SystemInfoRt.isMac && screenDevices != null && screenDevices.size > 1) {
    // use non-Retina
    for (screenDevice in screenDevices) {
      if (!UIUtil.isRetina(screenDevice)) {
        frame.setLocation(screenDevice.defaultConfiguration.bounds.x, frame.y)
        break
      }
    }
  }

  return frame
}

fun validateBounds(component: Container, snapshotDir: Path, snapshotName: String, isUpdateSnapshots: Boolean = isUpdateSnapshotsGlobal) {
  val snapshotFile = snapshotDir.resolve("$snapshotName.yml")
  val actualSerializedLayout: String
  if (component.layout is MigLayout) {
    actualSerializedLayout = serializeLayout(component)
  }
  else {
    val dumperOptions = DumperOptions()
    dumperOptions.lineBreak = DumperOptions.LineBreak.UNIX
    val yaml = Yaml(dumperOptions)
    actualSerializedLayout = yaml
      .dump(linkedMapOf("bounds" to component.components.map { it.bounds }))
      .replace(" !!java.awt.Rectangle", "")
  }

  if (!snapshotFile.exists() || isUpdateSnapshots) {
    snapshotFile.write(actualSerializedLayout)
    if (isUpdateSnapshots) {
      System.out.println("UPDATED snapshot ${snapshotFile.fileName}")
    }
  }
  else {
    assertThat(actualSerializedLayout).isEqualTo(snapshotFile)
  }
}

private val imageDirDefault by lazy { System.getenv("LAYOUT_IMAGE_REPO")?.let { Paths.get(it) } }

fun validateUsingImage(component: Component, snapshotRelativePath: String, isUpdateSnapshots: Boolean = isUpdateSnapshotsGlobal, imageDir: Path? = imageDirDefault) {
  if (imageDir == null) {
    System.out.println("Image validation is not used, set env LAYOUT_IMAGE_REPO to path to dir if need")
    return
  }

  val imagePath = imageDir.resolve("$snapshotRelativePath.png")
  if (!imagePath.exists()) {
    System.out.println("Write a new snapshot image ${imagePath.fileName}")
    component.writeAsImageToFile(imagePath)
    return
  }

  val oldImage = ImageIO.read(imagePath.toFile())
  @Suppress("UnnecessaryVariable")
  val snapshotComponent = component
  val newImage = componentToImage(snapshotComponent)
  try {
    Assertions.assertThat(newImage).isEqualTo(oldImage, Offset.offset(32))
  }
  catch (e: AssertionError) {
    if (oldImage.width == newImage.width && oldImage.height == newImage.height) {
      getDifferenceImage(oldImage, newImage)!!.writeToFile(imageDir.resolve("$snapshotRelativePath-DIFF.png"))
    }
    if (isUpdateSnapshots) {
      System.out.println("UPDATED snapshot image ${imagePath.fileName}")
      newImage.writeToFile(imagePath)
    }
    else {
      newImage.writeToFile(imageDir.resolve("$snapshotRelativePath-NEW.png"))
      throw e
    }
  }
}

// https://stackoverflow.com/a/25151302
fun getDifferenceImage(img1: BufferedImage, img2: BufferedImage): BufferedImage? {
  val w = img1.width
  val h = img1.height

  if (w != img2.width || h != img2.height) {
    throw RuntimeException("Image sizes are not equal")
  }

  val highlight = Color.MAGENTA.rgb
  val p1 = img1.getRGB(0, 0, w, h, null, 0, w)
  val p2 = img2.getRGB(0, 0, w, h, null, 0, w)
  var equals = true
  for (i in p1.indices) {
    if (p1[i] != p2[i]) {
      p1[i] = highlight
      equals = false
    }
  }

  if (equals) {
    return null
  }

  val out = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
  out.setRGB(0, 0, w, h, p1, 0, w)
  return out
}

val TestName.snapshotFileName: String
  get() {
    // remove parameters name
    val name = methodName
    val bracketIndex = name.lastIndexOf('[')
    return sanitizeFileName(if (bracketIndex > 0) name.substring(0, bracketIndex) else name)
  }

fun componentToImage(component: Component, type: Int = BufferedImage.TYPE_BYTE_GRAY): BufferedImage {
  return invokeAndWaitIfNeed {
    // we don't need retina image
    val image = BufferedImage(component.width, component.height, type)
    val g = image.graphics
    component.paint(g)
    g.dispose()
    image
  }
}

fun Component.writeAsImageToFile(file: Path) {
  componentToImage(this).writeToFile(file)
}

fun BufferedImage.writeToFile(file: Path) {
  file.outputStream().use {
    ImageIO.write(this, "png", it)
  }
}