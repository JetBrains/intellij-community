// FIR_COMPARISON
fun <T1, T2> T1.foo(handler: suspend (T2) -> Boolean) {}

fun f() {
    "".<caret>
}

// ELEMENT: foo
// TAIL_TEXT: " { handler: suspend (T2) -> Boolean } for T1 in <root>"