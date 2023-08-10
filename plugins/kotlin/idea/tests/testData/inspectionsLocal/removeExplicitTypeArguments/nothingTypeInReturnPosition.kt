// PROBLEM: none
// WITH_STDLIB
fun foo() {
    runCatching { return }.getOrThrow<Nothing><caret>()
}