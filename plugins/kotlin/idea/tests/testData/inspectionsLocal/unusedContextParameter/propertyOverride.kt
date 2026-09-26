// PROBLEM: none
// COMPILER_ARGUMENTS: -Xcontext-parameters
interface Foo {
    context(a: String)
    val v2: String get() = "x"
}

class Bar : Foo {
    context(<caret>a: String)
    override val v2: String
        get() = super.v2
}