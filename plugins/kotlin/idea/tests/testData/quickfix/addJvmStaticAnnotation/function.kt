// "Add '@JvmStatic' annotation to 'foo'" "true"
// WITH_STDLIB
open class A {
    companion object {
        protected fun foo() = 2
    }
}

class B : A() {
    fun bar() {
        print(<caret>foo())
    }
}
