package com.intellij.completion.ml.common

enum class CharCategory(val chars: CharSequence) {
  LETTER(('a'..'z').joinToString("")),
  CAPITAL_LETTER(('A'..'Z').joinToString("")),
  UNDERSCORE("_"),
  NUMBER(('0'..'9').joinToString("")),
  QUOTE("\"\'`"),
  OPENING_BRACKET("([{"),
  CLOSING_BRACKET(")]}"),
  SIGN("=+-></\\|"),
  PUNCTUATION(".,:;?!"),
  SYMBOL("#$%&*^@~");

  companion object {
    fun find(char: Char): CharCategory? = entries.find { it.chars.contains(char) }
  }
}