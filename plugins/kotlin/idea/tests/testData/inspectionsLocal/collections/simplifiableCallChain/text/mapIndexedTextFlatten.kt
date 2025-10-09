// WITH_STDLIB
package test

fun testMe() {
    sequenceOf(1, 2, 3).<caret>mapIndexed { i, x -> sequenceOf(i, x) }.flatten()
}