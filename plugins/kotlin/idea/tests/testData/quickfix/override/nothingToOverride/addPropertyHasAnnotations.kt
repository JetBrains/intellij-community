// "Add 'abstract val bar: Int' to 'I'" "true"
annotation class A(vararg val names: String)
annotation class B(val i: Int)

interface I {
}

class C : I {
    @A("x", "y")
    @B(1)
    <caret>override val bar = 1
}