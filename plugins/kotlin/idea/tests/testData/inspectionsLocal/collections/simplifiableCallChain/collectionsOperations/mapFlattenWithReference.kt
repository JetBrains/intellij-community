// WITH_STDLIB
package test

fun testMe() {
    listOf("hello").<caret>map(String::toList).flatten()
}