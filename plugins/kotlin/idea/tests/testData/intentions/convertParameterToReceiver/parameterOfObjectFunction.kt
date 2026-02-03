// WITH_STDLIB
object Foo {
    fun bar(<caret>p: String){}
}

fun test() {
    Foo.bar("abc")
}