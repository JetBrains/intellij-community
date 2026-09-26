// WITH_STDLIB
// PROBLEM: none
package test

suspend fun bar() {}

suspend fun foo() {
    <caret>runCatching {
        bar()
    }
}
