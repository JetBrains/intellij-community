// AFTER-WARNING: Variable 'foo' is never used
open class Foo {
    fun bar() {
        var<caret> foo = 1
    }
}