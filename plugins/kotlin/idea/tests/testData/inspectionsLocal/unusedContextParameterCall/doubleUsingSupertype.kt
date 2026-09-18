// PROBLEM: none
// COMPILER_ARGUMENTS: -Xcontext-parameters
open class A

class B : A()

context(p: A) fun f() {}

context(p: B)
fun g() {
    context(A()) {
        <caret>context(p) {
            f()
        }
    }
}