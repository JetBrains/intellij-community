// PROBLEM: 'also' has empty body
// FIX: none
// WITH_STDLIB

fun String.test() {
    <caret>also {  }
}