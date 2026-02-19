// PROBLEM: none
class UsedInConstructor(<caret>val x: Int) {
    fun foo(arg: Int) = arg

    constructor(): this(42) {
        foo(x)
    }
}