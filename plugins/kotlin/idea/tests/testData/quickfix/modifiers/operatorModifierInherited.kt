// "Add 'operator' modifier" "true"
open class Foo {
    fun get(idx: Int): Any = 5
}

object Bar : Foo()

fun test(): Any {
    return Bar<caret>[5]
}
