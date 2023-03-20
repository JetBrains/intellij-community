// WITH_STDLIB
inline fun <reified T> <caret>String.testFun(): String {
    return (T::class.java).name
}