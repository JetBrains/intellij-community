// WITH_STDLIB
package test

fun testMe() {
    listOf(1, 2, 3).<caret>map { listOf(it - 1, it + 1) }.flatten()
}