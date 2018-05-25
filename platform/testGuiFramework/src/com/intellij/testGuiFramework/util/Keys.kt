/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.testGuiFramework.util

enum class Key {
  ENTER,
  BACK_SPACE,
  TAB,
  CANCEL,
  CLEAR,
  PAUSE,
  CAPS_LOCK,
  ESCAPE,
  SPACE,
  PAGE_UP,
  PAGE_DOWN,
  END,
  HOME,
  LEFT,
  UP,
  RIGHT,
  DOWN,
  COMMA,
  MINUS,
  PERIOD,
  SLASH,
  d0,
  d1,
  d2,
  d3,
  d4,
  d5,
  d6,
  d7,
  d8,
  d9,
  SEMICOLON,
  EQUALS,
  A,
  B,
  C,
  D,
  E,
  F,
  G,
  H,
  I,
  J,
  K,
  L,
  M,
  N,
  O,
  P,
  Q,
  R,
  S,
  T,
  U,
  V,
  W,
  X,
  Y,
  Z,
  OPEN_BRACKET,
  BACK_SLASH,
  CLOSE_BRACKET,
  NUMPAD0,
  NUMPAD1,
  NUMPAD2,
  NUMPAD3,
  NUMPAD4,
  NUMPAD5,
  NUMPAD6,
  NUMPAD7,
  NUMPAD8,
  NUMPAD9,
  MULTIPLY,
  ADD,
  SEPARATER,
  SEPARATOR,
  SUBTRACT,
  DECIMAL,
  DIVIDE,
  DELETE,
  NUM_LOCK,
  SCROLL_LOCK,
  F1,
  F2,
  F3,
  F4,
  F5,
  F6,
  F7,
  F8,
  F9,
  F10,
  F11,
  F12,
  F13,
  F14,
  F15,
  F16,
  F17,
  F18,
  F19,
  F20,
  F21,
  F22,
  F23,
  F24,
  PRINTSCREEN,
  INSERT,
  HELP,
  BACK_QUOTE,
  QUOTE,
  KP_UP,
  KP_DOWN,
  KP_LEFT,
  KP_RIGHT,
  DEAD_GRAVE,
  DEAD_ACUTE,
  DEAD_CIRCUMFLEX,
  DEAD_TILDE,
  DEAD_MACRON,
  DEAD_BREVE,
  DEAD_ABOVEDOT,
  DEAD_DIAERESIS,
  DEAD_ABOVERING,
  DEAD_DOUBLEACUTE,
  DEAD_CARON,
  DEAD_CEDILLA,
  DEAD_OGONEK,
  DEAD_IOTA,
  DEAD_VOICED,
  DEAD_SEMIVOICED,
  AMPERSAND,
  ASTERISK,
  QUOTEDBL,
  LESS,
  GREATER,
  BRACELEFT,
  BRACERIGHT,
  AT,
  COLON,
  CIRCUMFLEX,
  DOLLAR,
  EURO_SIGN,
  EXCLAMATION_MARK,
  INVERTED_EXCLAMATION,
  LEFT_PARENTHESIS,
  NUMBER_SIGN,
  PLUS,
  RIGHT_PARENTHESIS,
  UNDERSCORE,
  WINDOWS,
  CONTEXT_MENU,
  FINAL,
  CONVERT,
  NONCONVERT,
  ACCEPT,
  MODECHANGE,
  KANA,
  KANJI,
  ALPHANUMERIC,
  KATAKANA,
  HIRAGANA,
  FULL_WIDTH,
  HALF_WIDTH,
  ROMAN_CHARACTERS,
  ALL_CANDIDATES,
  PREVIOUS_CANDIDATE,
  CODE_INPUT,
  JAPANESE_KATAKANA,
  JAPANESE_HIRAGANA,
  JAPANESE_ROMAN,
  KANA_LOCK,
  INPUT_METHOD,
  CUT,
  COPY,
  PASTE,
  UNDO,
  AGAIN,
  FIND,
  PROPS,
  STOP,
  COMPOSE,
  BEGIN,
  UNDEFINED
}

enum class Modifier {
  SHIFT,
  CONTROL,
  ALT,
  META,
  ALT_GR
}

class Shortcut(var modifiers: HashSet<Modifier> = HashSet(), var key: Key? = null) {

  fun getKeystroke(): String {
    val mods = modifiers.toList().sortedBy { it.ordinal }.joinToString(" ") { it.name.toLowerCase() }
    if (key == null) return ""
    val cleanKeyName = (if (key!!.name.startsWith("d")) key!!.name.substring(1) else key!!.name).toLowerCase()
    return if (mods.isNotEmpty()) "$mods $cleanKeyName" else cleanKeyName
  }

  override fun toString() = getKeystroke()
}

operator fun Modifier.plus(modifier: Modifier): Shortcut {
  return Shortcut(hashSetOf(this, modifier), null)
}

operator fun Modifier.plus(key: Key): Shortcut {
  return Shortcut(hashSetOf(this), key)
}

operator fun Shortcut.plus(modifier: Modifier): Shortcut {
  return Shortcut(hashSetOf(*this.modifiers.toTypedArray(), modifier), null)
}

operator fun Shortcut.plus(key: Key): Shortcut {
  if (this.key != null) throw Exception("Unable to merge shortcut with key ${this.key!!.name} and ${key.name}")
  return Shortcut(this.modifiers, key)
}

operator fun Modifier.plus(shortcut: Shortcut): Shortcut {
  val unionOfModifier = hashSetOf<Modifier>(this)
  unionOfModifier.addAll(shortcut.modifiers)
  return Shortcut(unionOfModifier, shortcut.key)
}

operator fun Shortcut.plus(shortcut: Shortcut): Shortcut {
  if (this.key != null && shortcut.key != null) throw Exception(
    "Unable to merge shortcut with key ${this.key!!.name} and ${shortcut.key!!.name}")
  val unionOfModifier = hashSetOf<Modifier>()
  unionOfModifier.addAll(this.modifiers)
  unionOfModifier.addAll(shortcut.modifiers)
  val newKey = if (this.key != null) this.key else shortcut.key
  return Shortcut(unionOfModifier, newKey)
}

fun resolveKey(inputString: String): Key {
  return if (Regex("\\d]").matches(inputString)) Key.valueOf("d$inputString")
  else Key.valueOf(inputString)
}