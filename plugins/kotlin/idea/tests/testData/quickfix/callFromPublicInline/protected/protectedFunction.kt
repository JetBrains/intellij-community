// "Make 'protectedMethod' public" "true"
open class Foo {
    protected fun protectedMethod() {}

    inline fun inlineFun() {
        <caret>protectedMethod()
    }
}
