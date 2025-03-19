// WITH_STDLIB
// PROBLEM: none
// IGNORE_K1

// Here the reified parameter is used in the receiver and the body, we should allow it
inline fun <reified T> <caret>Foo<T>.testFun(): String {
    return (T::class.java).name
}

class Foo<T>