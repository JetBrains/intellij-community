// PRIORITY: HIGH
// AFTER-WARNING: The expression is unused

import A.*

enum class A { A1, A2 }
enum class B { B1, B2 }

fun foo() {
    A1
    A2
    A::values

    <caret>B.B1
    B.B2
    B.values()
}
