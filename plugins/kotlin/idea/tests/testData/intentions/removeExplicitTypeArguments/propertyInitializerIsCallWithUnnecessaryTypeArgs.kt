// IS_APPLICABLE: true
// AFTER-WARNING: Variable 'l' is never used

fun bar() {
    val l: Foo<Int> = foo<caret><Int>()
}

class Foo<T>

fun <T> foo(): Foo<T> = Foo()
