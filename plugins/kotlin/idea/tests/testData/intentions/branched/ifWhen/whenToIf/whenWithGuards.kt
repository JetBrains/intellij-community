// WITH_STDLIB
// IGNORE_K1
// K2_ERROR: The feature "when guards" is experimental and should be enabled explicitly. This can be done by supplying the compiler argument '-Xwhen-guards', but note that no stability guarantees are provided.

private sealed class MySealed {
    object A : MySealed()
    class B(val x: Int) : MySealed()
}

private fun mySealed(s: MySealed) {
    when (s) {
        is MySealed.A -> println("1")
        is MySealed.B <caret>if (s.x > 5) -> { println("2") }
        else -> { println("3") }
    }
}
