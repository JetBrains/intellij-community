// WITH_STDLIB
// AFTER-WARNING: Parameter 'body' is never used
// AFTER-WARNING: Parameter 's' is never used

fun doo(s: String): String = "42"

fun Int.foo(body: (String) -> String) = Unit

fun main() {
    42.foo(::doo<caret>)
}