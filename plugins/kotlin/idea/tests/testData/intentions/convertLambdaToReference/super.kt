// IS_APPLICABLE: false
open class A {
    open fun method() {}
}

class B : A() {
    override fun method() {
        call <caret>{ super.method() }
    }
}

fun call(f: () -> Unit) {
    f()
}
