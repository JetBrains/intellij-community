// AFTER-WARNING: Variable 'foo' is never used
class A {
    init {
        val foo: String<caret>
        bar()
        foo = ""
    }

    fun bar() {}
}