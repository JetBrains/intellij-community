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
