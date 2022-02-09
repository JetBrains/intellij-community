// PROBLEM: none
// WITH_STDLIB
// COMPILER_ARGUMENTS: -Xopt-in=kotlin.RequiresOptIn

@RequiresOptIn
annotation class Marker

interface Base {
    @Marker
    fun foo()
}

class Derived : Base {
    @OptIn(Marker::class<caret>)
    override fun foo() {}
}
