// WITH_STDLIB
// FIX: none
// PROBLEM: Uses of 'println' should probably be replaced with more robust logging

fun println(s: String) {}

fun usage() {
    kotlin.io.<caret>println("hello") // qualified call to resolve to the STDLIB and not to the user-defined function
}