// FIR_IDENTICAL
// WITH_STDLIB
// CHECK_SYMBOL_NAMES
// HIGHLIGHTER_ATTRIBUTES_KEY
fun global() {
    fun inner() {

    }
    inner()
}

fun Int.ext() {
}

infix fun Int.fif(y: Int) {
    this * y
}

open class Container {
    open fun member() {
        global()
        5.ext()
        member()
        5 fif 6
    }
}

fun foo() {
    suspend {

    }
}

annotation class MySuspend
annotation class MyDynamic
annotation class MyExtension

@MySuspend
fun suspendFunction() {
}
@MyDynamic
fun dynamicFunction() {
}
@MyExtension
fun extensionFunction() {
}

fun testFunctionsWithSpecialAnnotations() {
    suspendFunction()
    dynamicFunction()
    extensionFunction()
}