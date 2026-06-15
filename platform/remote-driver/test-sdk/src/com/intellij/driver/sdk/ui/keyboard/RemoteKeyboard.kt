package com.intellij.driver.sdk.ui.keyboard

import com.intellij.driver.sdk.step
import com.intellij.driver.sdk.ui.remote.Robot
import com.intellij.openapi.diagnostic.logger
import java.awt.event.KeyEvent

class RemoteKeyboard(private val robot: Robot, isRemoteMac: () -> Boolean) {
  companion object {
    private val LOG
      get() = logger<RemoteKeyboard>()

    /**
     * To keep references up to date:
     * [com.intellij.driver.sdk.invokeAction]
     * [com.intellij.driver.sdk.invokeActionByShortcut]
     * Please update the message in RequiresOptIn if references change.
     */
    @RequiresOptIn(
      level = RequiresOptIn.Level.WARNING,
      message = "Prefer not to use hard-coded shortcuts, as it can cause test instability.\n" +
                "If you need to invoke an action as part of test setup, use `com.intellij.driver.sdk.invokeAction`.\n" +
                "If you want to verify that an action can be invoked via a shortcut, use `com.intellij.driver.sdk.invokeActionByShortcut`.\n" +
                "If you need to verify that a specific shortcut works, it is better to add a separate check for it.\n" +
                "If you have considered all of the above, add `@Suppress(\"OPT_IN_USAGE\")` to the statement."
    )
    @Retention(AnnotationRetention.BINARY)
    @Target(AnnotationTarget.FUNCTION)
    private annotation class DirectHotKeyUsage
  }

  val defaultModifierKey: Int by lazy { if (isRemoteMac()) KeyEvent.VK_META else KeyEvent.VK_CONTROL }

  fun key(key: Int): Unit = step("Press key $key") { robot.pressAndReleaseKey(key) }

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

  @DirectHotKeyUsage
  fun hotKeyWithDefaultModifierKey(vararg keyCodes: Int) {
    hotKey(defaultModifierKey, *keyCodes)
  }

  @DirectHotKeyUsage
  fun hotKey(vararg keyCodes: Int) {
    if (keyCodes.size == 1) {
      key(keyCodes.single())
      return
    }

    step("Press hotkeys ${keyCodes.joinToString(",") { "'$it'" }}.") {
      val lastKey = keyCodes.last()
      val others = keyCodes.dropLast(1)
      others.forEach {
        robot.pressKey(it)
        Thread.sleep(100)
      }
      key(lastKey)

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