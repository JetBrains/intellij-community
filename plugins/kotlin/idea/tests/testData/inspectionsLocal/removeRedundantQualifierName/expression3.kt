// WITH_STDLIB
package my.simple.name

fun main() {
    val a = kotlin.Int.MAX_VALUE
    val b = kotlin<caret>.Int.Companion.MAX_VALUE
    val c = kotlin.Int.Companion::MAX_VALUE
}

// IGNORE_FIR

// The test with companionOnClass.kt keeps "Companion" from `my.simple.name.Foo.Companion.VARIABLE`. It fixes the expression to
// `Foo.Companion.VARIABLE`. It is not clear why we have to drop "Companion" from `kotlin<caret>.Int.Companion.MAX_VALUE` in this test.