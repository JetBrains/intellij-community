// PROBLEM: none
// WITH_RUNTIME
// COMPILER_ARGUMENTS: -Xopt-in=kotlin.RequiresOptIn

@RequiresOptIn
annotation class Marker

open class Base {
    @Marker
    fun foo() {}
}

@OptIn(Marker::class)
class Derived : Base()

@OptIn(<caret>Marker::class)
fun bar(x: Derived) {
    x.foo()
}
