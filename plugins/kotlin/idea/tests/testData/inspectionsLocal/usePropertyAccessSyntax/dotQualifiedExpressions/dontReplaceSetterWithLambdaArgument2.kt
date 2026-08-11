// PROBLEM: none
// WITH_STDLIB
// ERROR: Unresolved reference: J
// K2_ERROR: UNRESOLVED_REFERENCE

fun test() {
    J().<caret>setR({ println("Hello, world!") })
}