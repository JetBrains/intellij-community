// WITH_STDLIB
// PROBLEM: none
package test

suspend fun bar() {}

suspend fun foo() {
    try {
        bar()
    } catch (<caret>e: Exception) {
    }
}
