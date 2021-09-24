// PROBLEM: none
// WITH_RUNTIME
// COMPILER_ARGUMENTS: -Xopt-in=kotlin.RequiresOptIn

@RequiresOptIn
annotation class Marker

class A {
    @Marker
    fun foo() {}
}

@OptIn(<caret>Marker::class)
fun bar(a: A) {
    a.foo()
}
