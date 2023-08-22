// WITH_STDLIB
// PROBLEM: none

class J(@JvmField val <caret>a: String) {

    fun foo() {
        println(a)
    }
}