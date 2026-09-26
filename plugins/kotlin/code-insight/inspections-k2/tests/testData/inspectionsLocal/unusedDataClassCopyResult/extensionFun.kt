// PROBLEM: none
// FIX: none
// WITH_STDLIB

fun Foo.copy(num: Int) = Unit

fun main() {
    val o = Foo("")
    o.<caret>copy(num = 42)
}

data class Foo(val prop: String)
