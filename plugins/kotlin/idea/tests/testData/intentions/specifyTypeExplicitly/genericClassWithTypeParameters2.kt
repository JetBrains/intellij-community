// WITH_STDLIB
// AFTER-WARNING: Parameter 'c' is never used
inline fun <reified T> foo<caret>() = of(T::class.java)

class Foo<F>
fun <F> of(c: Class<F>): Foo<F> = Foo()