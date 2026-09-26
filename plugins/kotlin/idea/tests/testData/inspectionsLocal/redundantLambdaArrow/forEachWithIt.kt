// WITH_STDLIB
// PROBLEM: "Redundant lambda arrow"

fun println(s: String) {}

fun test() {
    listOf(1, 2, 3).forEach { <caret>it -> println(it) }
}
