// PROBLEM: none
// WITH_STDLIB
// COMPILER_ARGUMENTS: -Xopt-in=kotlin.RequiresOptIn

@RequiresOptIn
annotation class Marker

abstract class Base {
    @Marker
    abstract val foo: Int
}

class Derived : Base() {
    @OptIn(<caret>Marker::class)
    override val foo: Int = 5
}
