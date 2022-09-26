// WITH_STDLIB
data class Foo(val a: String, val b: Int)

fun bar() {
    val block : (Foo) -> Unit = { (<caret>b, c) ->

    }
}