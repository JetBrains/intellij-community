// PROBLEM: none
// WITH_RUNTIME
// COMPILER_ARGUMENTS: -Xopt-in=kotlin.RequiresOptIn

@RequiresOptIn
annotation class Marker

@Marker
open class A {
    open fun foo() {}
}

@OptIn(<caret>Marker::class)
fun <E : A> bar(x: E) {}
