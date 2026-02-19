// WITH_STDLIB
// FIX: none
// PROBLEM: Uses of 'println' should probably be replaced with more robust logging

fun foo() {
    <caret>println("foo")
}
