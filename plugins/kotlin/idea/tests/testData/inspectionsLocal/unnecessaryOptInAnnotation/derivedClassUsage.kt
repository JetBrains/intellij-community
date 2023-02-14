// WITH_STDLIB
// COMPILER_ARGUMENTS: -Xopt-in=kotlin.RequiresOptIn

@RequiresOptIn
annotation class Marker

@Marker
open class A {
    open fun foo() {}
}

@OptIn(Marker::class)
class B : A() {
    override fun foo() {}
}

@OptIn(<caret>Marker::class)
fun bar(x: B) {
    x.foo()
}
