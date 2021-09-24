// PROBLEM: none
// WITH_RUNTIME
// COMPILER_ARGUMENTS: -Xopt-in=kotlin.RequiresOptIn

@RequiresOptIn
annotation class Marker

@Marker
class A {
    fun foo() {}
}

@OptIn(<caret>Marker::class)
typealias B = A

@OptIn(Marker::class)
fun bar() {
    val x = B()
    x.foo()
}
