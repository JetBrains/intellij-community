package p

object OOO {
    val vvv = 1
}

class C {
    fun foo() {
        "$v<caret>"
    }
}

// IGNORE_K2
// ELEMENT: vvv
// INVOCATION_COUNT: 2
