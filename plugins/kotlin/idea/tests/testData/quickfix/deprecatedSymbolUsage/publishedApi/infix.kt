// "Replace with generated @PublishedApi bridge call '`access$foo`(...)'" "true"
open class A {
    protected infix fun foo(p: Int) {
    }

    inline fun call() {
        A() foo<caret> 8
    }
}