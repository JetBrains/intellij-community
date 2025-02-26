// PROBLEM: none
// WITH_STDLIB
// COMPILER_ARGUMENTS: -Xopt-in=kotlin.RequiresOptIn

@RequiresOptIn
annotation class Marker

class Foo(@property:Marker val bar: Int = 0)

fun baz(foo: Foo): Int {
    @OptIn(<caret>Marker::class)
    return foo.bar
}
