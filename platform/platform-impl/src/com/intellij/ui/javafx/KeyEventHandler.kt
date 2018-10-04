// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.javafx

import com.intellij.ui.AppUIUtil
import javafx.event.EventHandler
import javafx.event.EventType
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent
import java.awt.KeyboardFocusManager
import java.awt.event.KeyListener
import java.awt.event.MouseEvent
import java.util.*


class KeyEventHandler(private val awtListener: KeyListener) : EventHandler<KeyEvent> {

  companion object {
    private val keyEventMap: Map<EventType<*>, Int>

    init {
      val map = HashMap<EventType<*>, Int>()
      map[javafx.scene.input.KeyEvent.KEY_PRESSED] = java.awt.event.KeyEvent.KEY_PRESSED
      map[javafx.scene.input.KeyEvent.KEY_RELEASED] = java.awt.event.KeyEvent.KEY_RELEASED
      map[javafx.scene.input.KeyEvent.KEY_TYPED] = java.awt.event.KeyEvent.KEY_TYPED
      keyEventMap = Collections.unmodifiableMap(map)
    }
  }

  override fun handle(ke: javafx.scene.input.KeyEvent) {
    val focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner ?: return

    val id = getKeyEventId(ke)
    val time = System.currentTimeMillis()
    val mods = getAWTModifiers(ke)
    val keyCode = getAWTKeyCode(ke)
    val keyChar = if (!ke.character.isEmpty())
      ke.character[0]
    else
      java.awt.event.KeyEvent.CHAR_UNDEFINED
    if (id == java.awt.event.KeyEvent.KEY_TYPED && keyChar == java.awt.event.KeyEvent.CHAR_UNDEFINED) {
      return
    }

    val kp = java.awt.event.KeyEvent(focusOwner, id, time, mods, keyCode, keyChar)
    AppUIUtil.invokeOnEdt {
      when (id) {
        java.awt.event.KeyEvent.KEY_PRESSED -> awtListener.keyPressed(kp)
        java.awt.event.KeyEvent.KEY_RELEASED -> awtListener.keyReleased(kp)
        java.awt.event.KeyEvent.KEY_TYPED -> awtListener.keyTyped(kp)
      }
    }
  }

  private fun getKeyEventId(ke: javafx.scene.input.KeyEvent): Int {
    return keyEventMap[ke.eventType]!!
  }

  private fun getAWTModifiers(jfxKeyEvent: javafx.scene.input.KeyEvent): Int {
    var mods = 0
    if (jfxKeyEvent.isAltDown) {
      mods = mods or (MouseEvent.ALT_MASK or MouseEvent.ALT_DOWN_MASK)
    }
    if (jfxKeyEvent.isControlDown) {
      mods = mods or (MouseEvent.CTRL_MASK or MouseEvent.CTRL_DOWN_MASK)
    }
    if (jfxKeyEvent.isShiftDown) {
      mods = mods or (MouseEvent.SHIFT_MASK or MouseEvent.SHIFT_DOWN_MASK)
    }
    if (jfxKeyEvent.isMetaDown) {
      mods = mods or (MouseEvent.META_MASK or MouseEvent.META_DOWN_MASK)
    }
    return mods
  }

  private fun getAWTKeyCode(jfxKeyEvent: javafx.scene.input.KeyEvent): Int {
    return when (jfxKeyEvent.code) {
      KeyCode.A ->  java.awt.event.KeyEvent.VK_A
      KeyCode.ACCEPT ->  java.awt.event.KeyEvent.VK_ACCEPT
      KeyCode.ADD ->  java.awt.event.KeyEvent.VK_ADD
      KeyCode.AGAIN ->  java.awt.event.KeyEvent.VK_AGAIN
      KeyCode.ALL_CANDIDATES ->  java.awt.event.KeyEvent.VK_ALL_CANDIDATES
      KeyCode.ALPHANUMERIC ->  java.awt.event.KeyEvent.VK_ALPHANUMERIC
      KeyCode.ALT ->  java.awt.event.KeyEvent.VK_ALT
      KeyCode.ALT_GRAPH ->  java.awt.event.KeyEvent.VK_ALT_GRAPH
      KeyCode.AMPERSAND ->  java.awt.event.KeyEvent.VK_AMPERSAND
      KeyCode.ASTERISK ->  java.awt.event.KeyEvent.VK_ASTERISK
      KeyCode.AT ->  java.awt.event.KeyEvent.VK_AT
      KeyCode.B ->  java.awt.event.KeyEvent.VK_B
      KeyCode.BACK_QUOTE ->  java.awt.event.KeyEvent.VK_BACK_QUOTE
      KeyCode.BACK_SLASH ->  java.awt.event.KeyEvent.VK_BACK_SLASH
      KeyCode.BACK_SPACE ->  java.awt.event.KeyEvent.VK_BACK_SPACE
      KeyCode.BEGIN ->  java.awt.event.KeyEvent.VK_BEGIN
      KeyCode.BRACELEFT ->  java.awt.event.KeyEvent.VK_BRACELEFT
      KeyCode.BRACERIGHT ->  java.awt.event.KeyEvent.VK_BRACERIGHT
      KeyCode.C ->  java.awt.event.KeyEvent.VK_C
      KeyCode.CANCEL ->  java.awt.event.KeyEvent.VK_CANCEL
      KeyCode.CAPS ->  java.awt.event.KeyEvent.VK_CAPS_LOCK
      KeyCode.CHANNEL_DOWN ->  java.awt.event.KeyEvent.VK_UNDEFINED
      KeyCode.CHANNEL_UP ->  java.awt.event.KeyEvent.VK_UNDEFINED
      KeyCode.CIRCUMFLEX ->  java.awt.event.KeyEvent.VK_CIRCUMFLEX
      KeyCode.CLEAR ->  java.awt.event.KeyEvent.VK_CLEAR
      KeyCode.CLOSE_BRACKET ->  java.awt.event.KeyEvent.VK_CLOSE_BRACKET
      KeyCode.CODE_INPUT ->  java.awt.event.KeyEvent.VK_CODE_INPUT
      KeyCode.COLON ->  java.awt.event.KeyEvent.VK_COLON
      KeyCode.COLORED_KEY_0 ->  java.awt.event.KeyEvent.VK_UNDEFINED
      KeyCode.COLORED_KEY_1 ->  java.awt.event.KeyEvent.VK_UNDEFINED
      KeyCode.COLORED_KEY_2 ->  java.awt.event.KeyEvent.VK_UNDEFINED
      KeyCode.COLORED_KEY_3 ->  java.awt.event.KeyEvent.VK_UNDEFINED
      KeyCode.COMMA ->  java.awt.event.KeyEvent.VK_COMMA
      KeyCode.COMPOSE ->  java.awt.event.KeyEvent.VK_COMPOSE
      KeyCode.CONTEXT_MENU ->  java.awt.event.KeyEvent.VK_CONTEXT_MENU
      KeyCode.CONTROL ->  java.awt.event.KeyEvent.VK_CONTROL
      KeyCode.CONVERT ->  java.awt.event.KeyEvent.VK_CONVERT
      KeyCode.COPY ->  java.awt.event.KeyEvent.VK_COPY
      KeyCode.CUT ->  java.awt.event.KeyEvent.VK_CUT
      KeyCode.D ->  java.awt.event.KeyEvent.VK_D
      KeyCode.DEAD_ABOVEDOT ->  java.awt.event.KeyEvent.VK_DEAD_ABOVEDOT
      KeyCode.DEAD_ABOVERING ->  java.awt.event.KeyEvent.VK_DEAD_ABOVERING
      KeyCode.DEAD_ACUTE ->  java.awt.event.KeyEvent.VK_DEAD_ACUTE
      KeyCode.DEAD_BREVE ->  java.awt.event.KeyEvent.VK_DEAD_BREVE
      KeyCode.DEAD_CARON ->  java.awt.event.KeyEvent.VK_DEAD_CARON
      KeyCode.DEAD_CEDILLA ->  java.awt.event.KeyEvent.VK_DEAD_CEDILLA
      KeyCode.DEAD_CIRCUMFLEX ->  java.awt.event.KeyEvent.VK_DEAD_CIRCUMFLEX
      KeyCode.DEAD_DIAERESIS ->  java.awt.event.KeyEvent.VK_DEAD_DIAERESIS
      KeyCode.DEAD_DOUBLEACUTE ->  java.awt.event.KeyEvent.VK_DEAD_DOUBLEACUTE
      KeyCode.DEAD_GRAVE ->  java.awt.event.KeyEvent.VK_DEAD_GRAVE
      KeyCode.DEAD_IOTA ->  java.awt.event.KeyEvent.VK_DEAD_IOTA
      KeyCode.DEAD_MACRON ->  java.awt.event.KeyEvent.VK_DEAD_MACRON
      KeyCode.DEAD_OGONEK ->  java.awt.event.KeyEvent.VK_DEAD_OGONEK
      KeyCode.DEAD_SEMIVOICED_SOUND ->  java.awt.event.KeyEvent.VK_DEAD_SEMIVOICED_SOUND
      KeyCode.DEAD_TILDE ->  java.awt.event.KeyEvent.VK_DEAD_TILDE
      KeyCode.DEAD_VOICED_SOUND ->  java.awt.event.KeyEvent.VK_DEAD_VOICED_SOUND
      KeyCode.DECIMAL ->  java.awt.event.KeyEvent.VK_DECIMAL
      KeyCode.DELETE ->  java.awt.event.KeyEvent.VK_DELETE
      KeyCode.DIGIT0 ->  java.awt.event.KeyEvent.VK_0
      KeyCode.DIGIT1 ->  java.awt.event.KeyEvent.VK_1
      KeyCode.DIGIT2 ->  java.awt.event.KeyEvent.VK_2
      KeyCode.DIGIT3 ->  java.awt.event.KeyEvent.VK_3
      KeyCode.DIGIT4 ->  java.awt.event.KeyEvent.VK_4
      KeyCode.DIGIT5 ->  java.awt.event.KeyEvent.VK_5
      KeyCode.DIGIT6 ->  java.awt.event.KeyEvent.VK_6
      KeyCode.DIGIT7 ->  java.awt.event.KeyEvent.VK_7
      KeyCode.DIGIT8 ->  java.awt.event.KeyEvent.VK_8
      KeyCode.DIGIT9 ->  java.awt.event.KeyEvent.VK_9
      KeyCode.DIVIDE ->  java.awt.event.KeyEvent.VK_DIVIDE
      KeyCode.DOLLAR ->  java.awt.event.KeyEvent.VK_DOLLAR
      KeyCode.DOWN ->  java.awt.event.KeyEvent.VK_DOWN
      KeyCode.E ->  java.awt.event.KeyEvent.VK_E
      KeyCode.EJECT_TOGGLE ->  java.awt.event.KeyEvent.VK_UNDEFINED
      KeyCode.END ->  java.awt.event.KeyEvent.VK_END
      KeyCode.ENTER ->  java.awt.event.KeyEvent.VK_ENTER
      KeyCode.EQUALS ->  java.awt.event.KeyEvent.VK_EQUALS
      KeyCode.ESCAPE ->  java.awt.event.KeyEvent.VK_ESCAPE
      KeyCode.EURO_SIGN ->  java.awt.event.KeyEvent.VK_EURO_SIGN
      KeyCode.EXCLAMATION_MARK ->  java.awt.event.KeyEvent.VK_EXCLAMATION_MARK
      KeyCode.F ->  java.awt.event.KeyEvent.VK_F
      KeyCode.F1 ->  java.awt.event.KeyEvent.VK_F1
      KeyCode.F10 ->  java.awt.event.KeyEvent.VK_F10
      KeyCode.F11 ->  java.awt.event.KeyEvent.VK_F11
      KeyCode.F12 ->  java.awt.event.KeyEvent.VK_F12
      KeyCode.F13 ->  java.awt.event.KeyEvent.VK_F13
      KeyCode.F14 ->  java.awt.event.KeyEvent.VK_F14
      KeyCode.F15 ->  java.awt.event.KeyEvent.VK_F15
      KeyCode.F16 ->  java.awt.event.KeyEvent.VK_F16
      KeyCode.F17 ->  java.awt.event.KeyEvent.VK_F17
      KeyCode.F18 ->  java.awt.event.KeyEvent.VK_F18
      KeyCode.F19 ->  java.awt.event.KeyEvent.VK_F19
      KeyCode.F2 ->  java.awt.event.KeyEvent.VK_F2
      KeyCode.F20 ->  java.awt.event.KeyEvent.VK_F20
      KeyCode.F21 ->  java.awt.event.KeyEvent.VK_F21
      KeyCode.F22 ->  java.awt.event.KeyEvent.VK_F22
      KeyCode.F23 ->  java.awt.event.KeyEvent.VK_F23
      KeyCode.F24 ->  java.awt.event.KeyEvent.VK_F24
      KeyCode.F3 ->  java.awt.event.KeyEvent.VK_F3
      KeyCode.F4 ->  java.awt.event.KeyEvent.VK_F4
      KeyCode.F5 ->  java.awt.event.KeyEvent.VK_F5
      KeyCode.F6 ->  java.awt.event.KeyEvent.VK_F6
      KeyCode.F7 ->  java.awt.event.KeyEvent.VK_F7
      KeyCode.F8 ->  java.awt.event.KeyEvent.VK_F8
      KeyCode.F9 ->  java.awt.event.KeyEvent.VK_F9
      KeyCode.FAST_FWD ->  java.awt.event.KeyEvent.VK_UNDEFINED
      KeyCode.FINAL ->  java.awt.event.KeyEvent.VK_FINAL
      KeyCode.FIND ->  java.awt.event.KeyEvent.VK_FIND
      KeyCode.FULL_WIDTH ->  java.awt.event.KeyEvent.VK_FULL_WIDTH
      KeyCode.G ->  java.awt.event.KeyEvent.VK_G
      KeyCode.GAME_A ->  java.awt.event.KeyEvent.VK_UNDEFINED
      KeyCode.GAME_B ->  java.awt.event.KeyEvent.VK_UNDEFINED
      KeyCode.GAME_C ->  java.awt.event.KeyEvent.VK_UNDEFINED
      KeyCode.GAME_D ->  java.awt.event.KeyEvent.VK_UNDEFINED
      KeyCode.GREATER ->  java.awt.event.KeyEvent.VK_GREATER
      KeyCode.H ->  java.awt.event.KeyEvent.VK_H
      KeyCode.HALF_WIDTH ->  java.awt.event.KeyEvent.VK_HALF_WIDTH
      KeyCode.HELP ->  java.awt.event.KeyEvent.VK_HELP
      KeyCode.HIRAGANA ->  java.awt.event.KeyEvent.VK_HIRAGANA
      KeyCode.HOME ->  java.awt.event.KeyEvent.VK_HOME
      KeyCode.I ->  java.awt.event.KeyEvent.VK_I
      KeyCode.INFO ->  java.awt.event.KeyEvent.VK_UNDEFINED
      KeyCode.INPUT_METHOD_ON_OFF ->  java.awt.event.KeyEvent.VK_INPUT_METHOD_ON_OFF
      KeyCode.INSERT ->  java.awt.event.KeyEvent.VK_INSERT
      KeyCode.INVERTED_EXCLAMATION_MARK ->  java.awt.event.KeyEvent.VK_UNDEFINED
      KeyCode.J ->  java.awt.event.KeyEvent.VK_J
      KeyCode.JAPANESE_HIRAGANA ->  java.awt.event.KeyEvent.VK_JAPANESE_HIRAGANA
      KeyCode.JAPANESE_KATAKANA ->  java.awt.event.KeyEvent.VK_JAPANESE_KATAKANA
      KeyCode.JAPANESE_ROMAN ->  java.awt.event.KeyEvent.VK_JAPANESE_ROMAN
      KeyCode.K ->  java.awt.event.KeyEvent.VK_K
      KeyCode.KANA ->  java.awt.event.KeyEvent.VK_KANA
      KeyCode.KANA_LOCK ->  java.awt.event.KeyEvent.VK_KANA_LOCK
      KeyCode.KANJI ->  java.awt.event.KeyEvent.VK_KANJI
      KeyCode.KATAKANA ->  java.awt.event.KeyEvent.VK_KATAKANA
      KeyCode.KP_DOWN ->  java.awt.event.KeyEvent.VK_KP_DOWN
      KeyCode.KP_LEFT ->  java.awt.event.KeyEvent.VK_KP_LEFT
      KeyCode.KP_RIGHT ->  java.awt.event.KeyEvent.VK_KP_RIGHT
      KeyCode.KP_UP ->  java.awt.event.KeyEvent.VK_KP_UP
      KeyCode.L ->  java.awt.event.KeyEvent.VK_L
      KeyCode.LEFT ->  java.awt.event.KeyEvent.VK_LEFT
      KeyCode.LEFT_PARENTHESIS ->  java.awt.event.KeyEvent.VK_LEFT_PARENTHESIS
      KeyCode.LESS ->  java.awt.event.KeyEvent.VK_LESS
      KeyCode.M ->  java.awt.event.KeyEvent.VK_M
      KeyCode.META ->  java.awt.event.KeyEvent.VK_META
      KeyCode.MINUS ->  java.awt.event.KeyEvent.VK_MINUS
      KeyCode.MODECHANGE ->  java.awt.event.KeyEvent.VK_MODECHANGE
      KeyCode.MULTIPLY ->  java.awt.event.KeyEvent.VK_MULTIPLY
      KeyCode.MUTE ->  java.awt.event.KeyEvent.VK_UNDEFINED
      KeyCode.N ->  java.awt.event.KeyEvent.VK_N
      KeyCode.NONCONVERT ->  java.awt.event.KeyEvent.VK_NONCONVERT
      KeyCode.NUMBER_SIGN ->  java.awt.event.KeyEvent.VK_NUMBER_SIGN
      KeyCode.NUMPAD0 ->  java.awt.event.KeyEvent.VK_NUMPAD0
      KeyCode.NUMPAD1 ->  java.awt.event.KeyEvent.VK_NUMPAD1
      KeyCode.NUMPAD2 ->  java.awt.event.KeyEvent.VK_NUMPAD2
      KeyCode.NUMPAD3 ->  java.awt.event.KeyEvent.VK_NUMPAD3
      KeyCode.NUMPAD4 ->  java.awt.event.KeyEvent.VK_NUMPAD4
      KeyCode.NUMPAD5 ->  java.awt.event.KeyEvent.VK_NUMPAD5
      KeyCode.NUMPAD6 ->  java.awt.event.KeyEvent.VK_NUMPAD6
      KeyCode.NUMPAD7 ->  java.awt.event.KeyEvent.VK_NUMPAD7
      KeyCode.NUMPAD8 ->  java.awt.event.KeyEvent.VK_NUMPAD8
      KeyCode.NUMPAD9 ->  java.awt.event.KeyEvent.VK_NUMPAD9
      KeyCode.NUM_LOCK ->  java.awt.event.KeyEvent.VK_NUM_LOCK
      KeyCode.O ->  java.awt.event.KeyEvent.VK_O
      KeyCode.OPEN_BRACKET ->  java.awt.event.KeyEvent.VK_OPEN_BRACKET
      KeyCode.P ->  java.awt.event.KeyEvent.VK_P
      KeyCode.PAGE_DOWN ->  java.awt.event.KeyEvent.VK_PAGE_DOWN
      KeyCode.PAGE_UP ->  java.awt.event.KeyEvent.VK_PAGE_UP
      KeyCode.PASTE ->  java.awt.event.KeyEvent.VK_PASTE
      KeyCode.PAUSE ->  java.awt.event.KeyEvent.VK_PAUSE
      KeyCode.PERIOD ->  java.awt.event.KeyEvent.VK_PERIOD
      KeyCode.PLAY ->  java.awt.event.KeyEvent.VK_UNDEFINED
      KeyCode.PLUS ->  java.awt.event.KeyEvent.VK_PLUS
      KeyCode.POUND ->  java.awt.event.KeyEvent.VK_UNDEFINED
      KeyCode.POWER ->  java.awt.event.KeyEvent.VK_UNDEFINED
      KeyCode.PREVIOUS_CANDIDATE ->  java.awt.event.KeyEvent.VK_PREVIOUS_CANDIDATE
      KeyCode.PRINTSCREEN ->  java.awt.event.KeyEvent.VK_PRINTSCREEN
      KeyCode.PROPS ->  java.awt.event.KeyEvent.VK_PROPS
      KeyCode.Q ->  java.awt.event.KeyEvent.VK_Q
      KeyCode.QUOTE ->  java.awt.event.KeyEvent.VK_QUOTE
      KeyCode.QUOTEDBL ->  java.awt.event.KeyEvent.VK_QUOTEDBL
      KeyCode.R ->  java.awt.event.KeyEvent.VK_R
      KeyCode.RECORD ->  java.awt.event.KeyEvent.VK_UNDEFINED
      KeyCode.REWIND ->  java.awt.event.KeyEvent.VK_UNDEFINED
      KeyCode.RIGHT ->  java.awt.event.KeyEvent.VK_RIGHT
      KeyCode.RIGHT_PARENTHESIS ->  java.awt.event.KeyEvent.VK_RIGHT_PARENTHESIS
      KeyCode.ROMAN_CHARACTERS ->  java.awt.event.KeyEvent.VK_ROMAN_CHARACTERS
      KeyCode.S ->  java.awt.event.KeyEvent.VK_S
      KeyCode.SCROLL_LOCK ->  java.awt.event.KeyEvent.VK_SCROLL_LOCK
      KeyCode.SEMICOLON ->  java.awt.event.KeyEvent.VK_SEMICOLON
      KeyCode.SEPARATOR ->  java.awt.event.KeyEvent.VK_SEPARATOR
      KeyCode.SHIFT ->  java.awt.event.KeyEvent.VK_SHIFT
      KeyCode.SHORTCUT ->  java.awt.event.KeyEvent.VK_UNDEFINED
      KeyCode.SLASH ->  java.awt.event.KeyEvent.VK_SLASH
      KeyCode.SOFTKEY_0 ->  java.awt.event.KeyEvent.VK_UNDEFINED
      KeyCode.SOFTKEY_1 ->  java.awt.event.KeyEvent.VK_UNDEFINED
      KeyCode.SOFTKEY_2 ->  java.awt.event.KeyEvent.VK_UNDEFINED
      KeyCode.SOFTKEY_3 ->  java.awt.event.KeyEvent.VK_UNDEFINED
      KeyCode.SOFTKEY_4 ->  java.awt.event.KeyEvent.VK_UNDEFINED
      KeyCode.SOFTKEY_5 ->  java.awt.event.KeyEvent.VK_UNDEFINED
      KeyCode.SOFTKEY_6 ->  java.awt.event.KeyEvent.VK_UNDEFINED
      KeyCode.SOFTKEY_7 ->  java.awt.event.KeyEvent.VK_UNDEFINED
      KeyCode.SOFTKEY_8 ->  java.awt.event.KeyEvent.VK_UNDEFINED
      KeyCode.SOFTKEY_9 ->  java.awt.event.KeyEvent.VK_UNDEFINED
      KeyCode.SPACE ->  java.awt.event.KeyEvent.VK_SPACE
      KeyCode.STAR ->  java.awt.event.KeyEvent.VK_UNDEFINED
      KeyCode.STOP ->  java.awt.event.KeyEvent.VK_STOP
      KeyCode.SUBTRACT ->  java.awt.event.KeyEvent.VK_SUBTRACT
      KeyCode.T ->  java.awt.event.KeyEvent.VK_T
      KeyCode.TAB ->  java.awt.event.KeyEvent.VK_TAB
      KeyCode.TRACK_NEXT ->  java.awt.event.KeyEvent.VK_UNDEFINED
      KeyCode.TRACK_PREV ->  java.awt.event.KeyEvent.VK_UNDEFINED
      KeyCode.U ->  java.awt.event.KeyEvent.VK_U
      KeyCode.UNDEFINED ->  java.awt.event.KeyEvent.VK_UNDEFINED
      KeyCode.UNDERSCORE ->  java.awt.event.KeyEvent.VK_UNDERSCORE
      KeyCode.UNDO ->  java.awt.event.KeyEvent.VK_UNDO
      KeyCode.UP ->  java.awt.event.KeyEvent.VK_UP
      KeyCode.V ->  java.awt.event.KeyEvent.VK_V
      KeyCode.VOLUME_DOWN ->  java.awt.event.KeyEvent.VK_UNDEFINED
      KeyCode.VOLUME_UP ->  java.awt.event.KeyEvent.VK_UNDEFINED
      KeyCode.W ->  java.awt.event.KeyEvent.VK_W
      KeyCode.WINDOWS ->  java.awt.event.KeyEvent.VK_WINDOWS
      KeyCode.X ->  java.awt.event.KeyEvent.VK_X
      KeyCode.Y ->  java.awt.event.KeyEvent.VK_Y
      KeyCode.Z ->  java.awt.event.KeyEvent.VK_Z
      else ->  java.awt.event.KeyEvent.VK_UNDEFINED
    }
  }
}