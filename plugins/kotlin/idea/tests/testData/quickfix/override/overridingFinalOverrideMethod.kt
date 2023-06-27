// "Make B.foo open" "true"
abstract class A {
    abstract fun foo()
}

open class B : A() {
    final override fun foo() {}
}

class C : B() {
    override<caret> fun foo() {}
}
/* IGNORE_FIR */
