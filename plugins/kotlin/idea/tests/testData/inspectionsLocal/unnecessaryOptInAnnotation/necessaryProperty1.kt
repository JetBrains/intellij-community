// PROBLEM: none
// WITH_RUNTIME
// COMPILER_ARGUMENTS: -Xopt-in=kotlin.RequiresOptIn

@RequiresOptIn
annotation class Marker

class Foo {
    @Marker
    var bar: Int = 0
}

@OptIn(<caret>Marker::class)
fun baz(foo: Foo): Int {
    return foo.bar
}
