// "Replace with 'newFun(*elements)'" "true"
// WITH_STDLIB

@Deprecated("", ReplaceWith("newFun(*elements)"))
fun <T> oldFun(vararg elements: T) {
    newFun(*elements)
}

fun <T> newFun(vararg elements: T){}

fun foo() {
    <caret>oldFun(bar())
}

fun bar(): java.io.File? = null
