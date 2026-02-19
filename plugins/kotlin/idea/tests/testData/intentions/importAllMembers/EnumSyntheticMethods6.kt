// PRIORITY: HIGH
import A.*

enum class A { A1, A2 }
enum class B { B1, B2 }

fun foo() {
    A1
    A2
    values()

    <caret>B.B1
    B.B2
    B.values()
}

fun values() {}
