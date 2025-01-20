// PROBLEM: none
// WITH_STDLIB
// ERROR: Unresolved reference: J
// K2_ERROR: Unresolved reference 'J'.

fun test() {
    J().<caret>setR({ println("Hello, world!") })
}