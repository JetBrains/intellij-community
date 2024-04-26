// WITH_STDLIB
// FIX: none
// PROBLEM: Uses of 'print' should probably be replaced with more robust logging

fun foo() {
    <caret>print(0)
}