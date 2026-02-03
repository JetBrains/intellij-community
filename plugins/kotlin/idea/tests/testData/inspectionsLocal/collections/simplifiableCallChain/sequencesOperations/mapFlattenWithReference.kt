// WITH_STDLIB
package test

fun testMe() {
    sequenceOf("hello").<caret>map(String::asSequence).flatten()
}