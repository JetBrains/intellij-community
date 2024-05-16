// FIR_IDENTICAL
// FIR_COMPARISON
object O {
    private val zzzz = 0
}

fun foo() {
    O.zz<caret>
}

// INVOCATION_COUNT: 0
// NUMBER: 0
