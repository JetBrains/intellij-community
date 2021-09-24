// PROBLEM: none
// WITH_RUNTIME
// COMPILER_ARGUMENTS: -Xopt-in=kotlin.RequiresOptIn

@RequiresOptIn
annotation class Marker

class Foo() {
    val bar = Array<Int>(5) { 0 }

    @Marker
    operator fun set(index: Int, value: Int) {
        bar[index] = value
    }
}

@OptIn(<caret>Marker::class)
fun baz(foo: Foo) {
    foo[2] = 1
}
