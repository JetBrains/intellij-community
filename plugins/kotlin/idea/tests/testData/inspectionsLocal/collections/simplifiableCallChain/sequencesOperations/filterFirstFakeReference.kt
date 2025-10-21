// PROBLEM: none
// WITH_STDLIB

val x = sequenceOf('1', 'a', 0.toChar()).<caret>filter { it.toInt() != 0 }.first(Char::isLetter)