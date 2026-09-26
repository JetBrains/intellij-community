// FIX: "Replace with '?: error(…)'"
fun main() {
    val foo: String? = "foo"
    val x = foo<caret>!!
}
