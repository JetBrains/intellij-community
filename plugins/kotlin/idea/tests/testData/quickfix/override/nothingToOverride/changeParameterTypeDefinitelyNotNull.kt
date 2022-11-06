// "Change function signature to 'fun <T> f(a: (T & Any).() -> Unit)'" "true"
open class A {
    open fun <T> f(a: (T & Any).() -> Unit) {}
}

class B : A() {
    <caret>override fun f(a: String) {}
}
