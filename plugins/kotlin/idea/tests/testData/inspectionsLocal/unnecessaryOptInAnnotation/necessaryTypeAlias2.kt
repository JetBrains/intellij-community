// PROBLEM: none
// WITH_RUNTIME
// COMPILER_ARGUMENTS: -Xopt-in=kotlin.RequiresOptIn

@RequiresOptIn
@Target(AnnotationTarget.TYPEALIAS)
annotation class Marker

class A {
    fun foo() {}
}

@Marker
typealias B = A

fun bar() {
    @OptIn(Marker::class<caret>)
    val x = B()

    @OptIn(Marker::class)
    x.foo()
}
