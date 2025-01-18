// PROBLEM: none
// K2-ERROR: Unresolved reference 'J'.

fun test() {
    J().<caret>setR({ println("Hello, world!") })
}