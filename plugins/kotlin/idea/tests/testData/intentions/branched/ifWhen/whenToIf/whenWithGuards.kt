// WITH_STDLIB

// COMPILER_ARGUMENTS: -Xwhen-guards

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
