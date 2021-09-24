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

@OptIn(Marker::class<caret>)
fun bar() {
    val x = A()
    x.foo()
}
