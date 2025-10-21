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

  fun key(key: Int) = step("Press key $key") { robot.pressAndReleaseKey(key) }

  fun enter() {
    step("Press enter") {
      key(KeyEvent.VK_ENTER)
    }
  }

  fun escape() {
    step("Press escape") {
      key(KeyEvent.VK_ESCAPE)
    }
  }

  fun down() {
    step("Press down") {
      key(KeyEvent.VK_DOWN)
    }
  }

  fun up() {
    step("Press up") {
      key(KeyEvent.VK_UP)
    }
  }

  fun left() {
    step("Press left") {
      key(KeyEvent.VK_LEFT)
    }
  }

  fun right() {
    step("Press right") {
      key(KeyEvent.VK_RIGHT)
    }
  }

  fun backspace() {
    step("Press backspace") {
      key(KeyEvent.VK_BACK_SPACE)
    }
  }

  fun tab() {
    step("Press tab") {
      key(KeyEvent.VK_TAB)
    }
  }

  fun space() {
    step("Press space") {
      key(KeyEvent.VK_SPACE)
    }
  }

  fun hotKey(vararg keyCodes: Int) {
    step("Press hotkeys ${keyCodes.joinToString(",") { "'$it'" }}.") {
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
  }

  fun typeText(text: String, delayBetweenCharsInMs: Long = 150) {
    step("Type text '$text'") {
      text.forEach {
        robot.type(it)
        Thread.sleep(delayBetweenCharsInMs)
      }
    }
  }

  fun pressing(key: Int, doWhilePress: RemoteKeyboard.() -> Unit) {
    step("Pressing $key.") {
      try {
        robot.pressKey(key)
        this.doWhilePress()
      }
      finally {
        robot.releaseKey(key)
      }
    }
  }

  fun doublePressing(key: Int, doWhilePress: RemoteKeyboard.() -> Unit) {
    step("Double pressing $key.") {
      try {
        robot.doublePressKeyAndHold(key)
        this.doWhilePress()
      }
      finally {
        robot.releaseKey(key)
      }
    }
  }
}