// FIR_COMPARISON
package test

enum class E {
    ;
    companion object {
        fun method1() {
        }
    }
}

fun E.Companion.method2() {
}

fun main() {
    E.m<caret>
}

// EXIST: method1
// EXIST: method2