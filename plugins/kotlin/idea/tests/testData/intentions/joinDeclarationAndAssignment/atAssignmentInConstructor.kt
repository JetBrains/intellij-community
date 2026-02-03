// AFTER-WARNING: Variable 'foo' is never used
class A {
    constructor() {
        val foo: String<caret>
        bar()
        foo = ""
    }

    fun bar() {}
}
