package org.jetbrains.completion.full.line

enum class ReferenceCorrectness {
  UNDEFINED,
  INCORRECT,
  CORRECT, ;

  fun isCorrect(): Boolean {
    return this != INCORRECT
  }
}
