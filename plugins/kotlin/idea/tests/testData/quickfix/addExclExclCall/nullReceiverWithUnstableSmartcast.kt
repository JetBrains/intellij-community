// "Add non-null asserted (!!) call" "true"

interface Foo {
    fun bar()
}

open class MyClass {
    open val a: Foo? = null

    fun foo() {
        if (a == null) {
            a<caret>.bar()
        }
    }
}