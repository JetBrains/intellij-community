// PROBLEM: none
// WITH_RUNTIME
// COMPILER_ARGUMENTS: -Xopt-in=kotlin.RequiresOptIn

@RequiresOptIn
annotation class Marker

class Foo {
    var bar: Int = 0
        @Marker
        set(value) {
            field = value
        }
}

@OptIn(<caret>Marker::class)
fun baz(foo: Foo) {
    foo.bar += 1
}
