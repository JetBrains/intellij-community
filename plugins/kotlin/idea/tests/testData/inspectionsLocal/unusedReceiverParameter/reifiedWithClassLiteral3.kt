// WITH_STDLIB
inline fun <reified T> <caret>Foo<T>.testFun(): String {
    return (T::class.java).name
}

class Foo<T>