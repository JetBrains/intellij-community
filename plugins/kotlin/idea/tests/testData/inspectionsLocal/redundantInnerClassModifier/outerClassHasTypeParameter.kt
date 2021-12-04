class Outer<T, U> {
    fun t(t: T) = t
    fun u(u: U) = u
    <caret>inner class Inner
}

open class C<T>

val c = C<Outer<String, Int>.Inner>()

class F : C<Outer<String, Int>.Inner>()

fun Outer<String, Int>.Inner.extF() {}
