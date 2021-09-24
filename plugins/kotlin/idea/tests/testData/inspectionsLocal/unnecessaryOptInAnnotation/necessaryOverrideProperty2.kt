// PROBLEM: none
// WITH_RUNTIME
// COMPILER_ARGUMENTS: -Xopt-in=kotlin.RequiresOptIn

@RequiresOptIn
annotation class Marker

abstract class Base {
    @Marker
    abstract val foo: Int
}

class Derived(@OptIn(Marker::class<caret>) override val foo: Int) : Base()
