// IS_APPLICABLE: false
// WITH_STDLIB
// Issue: KTIJ-31745

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

// Not enabled to have the test work for K1: -Xwhen-guards doesn't fix the error because of a low language version
// ERROR: The feature "when guards" is experimental and should be enabled explicitly. This can be done by supplying the compiler argument '-Xwhen-guards', but note that no stability guarantees are provided.
