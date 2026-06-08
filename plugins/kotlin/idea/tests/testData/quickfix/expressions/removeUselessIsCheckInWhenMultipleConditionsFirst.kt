// "Remove redundant 'is' check" "true"
open class A
class B: A()
class C: A()
class D

fun test(a: A) {
    when (a) {
        is B -> {
            println("B")
        }
        <caret>is D, is C -> {
            println("C or D")
        }
    }
}


// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveUselessIsCheckFixForWhen
