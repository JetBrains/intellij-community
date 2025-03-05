// PROBLEM: none
// IGNORE_K1
fun foo() {
    var a: Boolean? = null
    var b: Boolean? = null
    if (a <caret>?: b ?: false) {

    }
}