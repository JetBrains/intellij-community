// HIGHLIGHT: WARNING
// PROBLEM: 'if' expression has identical branches
// FIX: none

fun nextFlag(): Boolean = true

fun test() {
    if (nextFlag()) <caret>{} else {}
}
