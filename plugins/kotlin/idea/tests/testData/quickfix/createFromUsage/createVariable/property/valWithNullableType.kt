// "Create member property 'A.foo'" "true"
// ERROR: Property must be initialized or be abstract

class A<T>(val n: T)

fun test() {
    val a: A<Int>? = A(1).<caret>foo
}
