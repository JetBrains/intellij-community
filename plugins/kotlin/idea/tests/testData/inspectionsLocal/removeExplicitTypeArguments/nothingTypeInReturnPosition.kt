// PROBLEM: none
// WITH_STDLIB
fun foo() {
    runCatching { return }.getOrThrow<Nothing><caret>()
}

// IGNORE_K2
// KT-60878
// KTIJ-32890