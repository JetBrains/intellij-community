// "Add 'abstract fun foo()' to 'I'" "true"
annotation class A(vararg val names: String)
annotation class B(val i: Int)

interface I

class C : I {
    @A("x", "y")
    @B(1)
    <caret>override fun foo() {}
}