// "Add non-null asserted (!!) call" "true"
// ACTION: Add non-null asserted (!!) call
// ACTION: Convert to run
// ACTION: Convert to with
// ACTION: Remove braces from 'if' statement
// ACTION: Replace 'if' expression with safe access expression

interface Foo {
    fun bar()
}

open class MyClass {
    open val a: Foo? = null

    fun foo() {
        if (a != null) {
            <caret>a.bar()
        }
    }
}