// IS_APPLICABLE: true
// WITH_STDLIB

fun foo() {
    listOf(1).forEach { run { <caret>it.bar() } }
}

fun Int.bar() {
}