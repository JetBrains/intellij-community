// WITH_RUNTIME

fun doo(i: Int, s: String): String = "42"

fun Int.foo(body: Int.(String) -> String) = Unit

fun main() {
    42.foo(::doo<caret>)
}