// WITH_STDLIB
// IGNORE_K1
// K2_ERROR: The feature "when guards" is experimental and should be enabled explicitly. This can be done by supplying the compiler argument '-Xwhen-guards', but note that no stability guarantees are provided.

private fun test(s: Any) {
    when (s) {
        is String -> println("1")
        is Int <caret>if (s > 5 || s < 3) -> { println("2") }
        else -> { println("3") }
    }
}
