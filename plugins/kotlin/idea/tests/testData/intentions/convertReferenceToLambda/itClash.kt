// IS_APPLICABLE: true
// WITH_STDLIB
// AFTER-WARNING: Parameter 'arg' is never used

fun foo() {
    listOf(1).forEach { (-it).let(<caret>it::bar) }
}

fun Int.bar(arg: Int) {
}