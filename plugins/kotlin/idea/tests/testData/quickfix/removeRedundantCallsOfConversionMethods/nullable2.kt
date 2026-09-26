// "Remove redundant calls of the conversion method" "false"
// WITH_STDLIB
data class Foo(val name: String)

fun nullable2(foo: Foo?) {
    val s: String = foo?.name.toString()<caret>
}