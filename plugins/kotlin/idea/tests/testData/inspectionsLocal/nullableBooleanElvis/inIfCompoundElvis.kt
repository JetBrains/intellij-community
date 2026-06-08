// PROBLEM: none

fun foo() {
    var a: Boolean? = null
    var b: Boolean? = null
    if (a <caret>?: b ?: false) {

    }
}