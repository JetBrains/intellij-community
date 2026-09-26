// PROBLEM: none
// WITH_STDLIB
interface A
class AImpl : A {
    val foo: String = ""
}

class B<T : A> {
    fun implicitOut(block: () -> T) {}
    fun use(block: (T) -> Unit) { }
}

fun <T : A> dsl(block: B<T>.() -> Unit): B<T> {
    return B<T>().apply(block)
}

fun main() {
    dsl<AImpl><caret> {
        implicitOut { AImpl() }
        use { it.foo }
    }
}

// IGNORE_K2
// Since Kotlin 2.1, type inference can correctly infer the type without it being explicitly specified.