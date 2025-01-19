// PROBLEM: none
// WITH_STDLIB
// ERROR: Unresolved reference: J
// K2-ERROR: Unresolved reference 'J'.

fun test() {
    J().<caret>setR({ println("Hello, world!") })
}