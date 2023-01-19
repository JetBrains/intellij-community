// PROBLEM: none

open class A

class B : A() {
    fun test(): String {
        return "B = ${super.<caret>toString()}"
    }
}