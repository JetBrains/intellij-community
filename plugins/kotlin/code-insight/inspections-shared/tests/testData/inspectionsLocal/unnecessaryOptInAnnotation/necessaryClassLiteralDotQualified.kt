// PROBLEM: none
// WITH_STDLIB
// COMPILER_ARGUMENTS: -Xopt-in=kotlin.RequiresOptIn

@RequiresOptIn
annotation class Marker

object Outer {
    @Marker
    class A {
        fun foo() {}
    }
}

@OptIn(<caret>Marker::class)
fun bar() {
    Outer.A::class
}
