// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.text.matching

/**
 * Converts characters typed in one keyboard layout to their equivalents in another layout
 * based on physical key positions.
 *
 * This is used to fix common user mistakes when typing search patterns in the wrong keyboard layout.
 * For example, a user with a Russian keyboard layout might accidentally type "Пше" (Cyrillic characters)
 * when they meant to type "Git" (Latin characters on the same physical keys).
 *
 * @see com.intellij.psi.codeStyle.FixingLayoutMatcher
 */
fun interface KeyboardLayoutConverter {
  companion object {
    /**
     * A no-op converter that disables keyboard layout correction.
     * Always returns `null`, indicating no alternative layout is available.
     */
    val noop: KeyboardLayoutConverter = KeyboardLayoutConverter { null }
  }

  /**
   * Converts a character from one keyboard layout to its equivalent in another layout
   * based on physical key position.
   *
   * @param c The character to convert
   * @return The equivalent character in the target layout, or `null` if:
   *   - The character is already in the target layout (e.g., ASCII character for English layout)
   *   - No mapping exists for this character
   *   - Conversion is not supported
   */
  fun convert(c: Char): Char?
}

internal object RussianToEnglishKeyboardLayoutConverter : KeyboardLayoutConverter {
  private val LL: Map<Char, Char> = mapOf(
    'й' to 'q', 'ц' to 'w', 'у' to 'e', 'к' to 'r', 'е' to 't', 'н' to 'y', 'г' to 'u', 'ш' to 'i',
    'щ' to 'o', 'з' to 'p', 'х' to '[', 'ъ' to ']', 'ф' to 'a', 'ы' to 's', 'в' to 'd', 'а' to 'f',
    'п' to 'g', 'р' to 'h', 'о' to 'j', 'л' to 'k', 'д' to 'l', 'ж' to ';', 'э' to '\'', 'я' to 'z',
    'ч' to 'x', 'с' to 'c', 'м' to 'v', 'и' to 'b', 'т' to 'n', 'ь' to 'm', 'б' to ',', 'ю' to '.',
    '.' to '/'
  )

  override fun convert(c: Char): Char? = LL[c]
}