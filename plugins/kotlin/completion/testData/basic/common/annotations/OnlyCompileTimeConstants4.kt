// FIR_COMPARISON
// FIR_IDENTICAL

object C {
    val nul: String = ""
}

annotation class A(val s: String)

@A(nul<caret>)
class B

// ABSENT: nullsFirst
// ABSENT: nul
// INVOCATION_COUNT: 1