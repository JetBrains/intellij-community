// HIGHLIGHT: GENERIC_ERROR_OR_WARNING
open class A {
    open operator fun set(s: String, value: Int) {}
}

open class B : A() {
    override fun set(s: String, value: Int) {
        super.set(s, value)
    }
}

class C : B() {
    override fun set(s: String, value: Int) {
        super.set(s, value)
    }
}

fun foo() {
    C().set<caret>("x", 1)
}
