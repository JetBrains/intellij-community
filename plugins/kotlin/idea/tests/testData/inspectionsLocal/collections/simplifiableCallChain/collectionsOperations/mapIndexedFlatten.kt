// WITH_STDLIB
package test

fun testMe() {
    listOf(1, 2, 3).<caret>mapIndexed { i, x -> listOf(i, x) }.flatten()
}