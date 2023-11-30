// IGNORE_K1
sealed class Sealed

class A: Sealed()
class B: Sealed()

val sealedConstant: Sealed = A()

fun test(s: Sealed) {
    when (s) {
        <caret>
    }
}

// WITH_ORDER
// EXIST: is A
// EXIST: is B
// EXIST: sealedConstant