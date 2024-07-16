package com.intellij.driver.sdk.screenshot

import java.awt.Image
import java.awt.Rectangle
import java.awt.Robot
import java.awt.Toolkit
import java.awt.image.BufferedImage

fun Image.toBufferedImage(): BufferedImage {
  if (this is BufferedImage) {
    return this
  }
  val bufferedImage = BufferedImage(this.getWidth(null), this.getHeight(null), BufferedImage.TYPE_INT_ARGB)

  val graphics2D = bufferedImage.createGraphics()
  graphics2D.drawImage(this, 0, 0, null)
  graphics2D.dispose()

  return bufferedImage
}

fun takeScreenshot(rectangle: Rectangle): BufferedImage {
  val robot = Robot()
  return robot.createScreenCapture(rectangle)
}

fun takeFullscreenScreenshot(): BufferedImage {
  val rectangle = Rectangle(Toolkit.getDefaultToolkit().screenSize)
  return takeScreenshot(rectangle)
}