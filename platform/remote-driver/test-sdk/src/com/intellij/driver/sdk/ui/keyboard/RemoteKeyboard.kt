package com.intellij.driver.sdk.ui.keyboard

import com.intellij.driver.sdk.step
import com.intellij.driver.sdk.ui.remote.Robot
import com.intellij.openapi.diagnostic.logger
import java.awt.event.KeyEvent

class RemoteKeyboard(private val robot: Robot) {
  companion object {
    private val LOG
      get() = logger<RemoteKeyboard>()
  }

  fun key(key: Int) = robot.pressAndReleaseKey(key)

  fun enter() {
    LOG.info("Pressing enter")
    key(KeyEvent.VK_ENTER)
  }

  fun escape() {
    LOG.info("Pressing escape")
    key(KeyEvent.VK_ESCAPE)
  }

  fun down() {
    LOG.info("Pressing down")
    key(KeyEvent.VK_DOWN)
  }

  fun up() {
    LOG.info("Pressing up")
    key(KeyEvent.VK_UP)
  }

  fun left() {
    LOG.info("Pressing left")
    key(KeyEvent.VK_LEFT)
  }

  fun right() {
    LOG.info("Pressing right")
    key(KeyEvent.VK_RIGHT)
  }

  fun backspace() {
    LOG.info("Pressing backspace")
    key(KeyEvent.VK_BACK_SPACE)
  }

  fun tab() {
    LOG.info("Pressing tab")
    key(KeyEvent.VK_TAB)
  }

  fun space() {
    LOG.info("Pressing space")
    key(KeyEvent.VK_SPACE)
  }

  fun hotKey(vararg keyCodes: Int) {
    LOG.info("Pressing hotkeys ${keyCodes.joinToString(",") { "'$it'" }}.")

    val lastKey = keyCodes.last()
    val others = keyCodes.dropLast(1)
    others.forEach {
      robot.pressKey(it)
      Thread.sleep(100)
    }
    robot.pressAndReleaseKey(lastKey)

    others.reversed().forEach {
      robot.releaseKey(it)
    }
  }

  fun typeText(text: String, delayBetweenCharsInMs: Long = 50) {
    step("Type text '$text'") {
      LOG.info("Entering text '$text'.")
      text.forEach {
        robot.type(it)
        Thread.sleep(delayBetweenCharsInMs)
      }
    }
  }

  fun pressing(key: Int, doWhilePress: RemoteKeyboard.() -> Unit) {
    LOG.info("Performing action while pressing $key.")

    try {
      robot.pressKey(key)
      this.doWhilePress()
    }
    finally {
      robot.releaseKey(key)
    }
  }

  fun doublePressing(key: Int, doWhilePress: RemoteKeyboard.() -> Unit) {
    LOG.info("Performing action while double pressing $key.")

    try {
      robot.doublePressKeyAndHold(key)
      this.doWhilePress()
    }
    finally {
      robot.releaseKey(key)
    }
  }
}