// WITH_STDLIB
// IGNORE_K2
object Foo {
    fun bar(<caret>p: String){}
}

fun test() {
    Foo.bar("abc")
}