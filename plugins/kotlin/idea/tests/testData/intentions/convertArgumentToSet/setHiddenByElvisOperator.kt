// WITH_STDLIB
// IS_APPLICABLE: false
// PROBLEM: none

fun foo(a: String, b: String, c: String, array: Array<String>? ) {
    listOf(a, b, c) - (array?.toSet() ?: emptySet())<caret>
}

// IGNORE_K1