// "Remove 'inline' modifier" "true"
open class Foo {
    protected fun protectedMethod() {}

    inline val inlineProperty: Int
        get() {
            <caret>protectedMethod()
            return 42
        }
}
