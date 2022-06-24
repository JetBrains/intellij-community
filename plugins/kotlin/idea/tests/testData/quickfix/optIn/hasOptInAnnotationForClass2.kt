// "Opt in for 'B' on containing class 'C'" "true"
// WITH_STDLIB
@RequiresOptIn
annotation class A

@RequiresOptIn
annotation class B

interface I {
    @A
    fun foo(): Unit

    @B
    fun bar(): Unit
}

@OptIn(A::class)
class C : I {
    override fun foo() {}
    override fun <caret>bar() {}
}