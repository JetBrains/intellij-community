// PROBLEM: none
// Issue: KTIJ-31571

abstract class A {
    abstract fun merge(a: A)
}

class B: A() {
    var v: Int = 0

    override fun merge(a: A) {
        v = (a as B).v<caret>
    }
}
