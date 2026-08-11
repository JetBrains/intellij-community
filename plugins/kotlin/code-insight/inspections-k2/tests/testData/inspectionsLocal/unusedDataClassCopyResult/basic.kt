// PROBLEM: Unused result of data class copy
// FIX: none
// WITH_STDLIB
fun main() {
    val o = Foo("")
    o.<caret>copy(prop = "New")
    bar(o)
}

data class Foo(val prop: String)

fun bar(foo: Foo) {}