// WITH_STDLIB
// AFTER-WARNING: Variable 's' is never used
data class Foo(val name: String)

fun test(foo: Foo?) {
    val s: String? = foo?.name?.toString()<caret>
}