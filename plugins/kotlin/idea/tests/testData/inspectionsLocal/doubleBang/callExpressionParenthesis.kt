// FIX: "Replace with '?: error(…)'"
fun foo(): String? = "foo"

fun main() {
    (foo())<caret>!!
}
