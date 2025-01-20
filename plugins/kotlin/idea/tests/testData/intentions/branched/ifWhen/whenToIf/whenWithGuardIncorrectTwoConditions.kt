// WITH_STDLIB
// IGNORE_K1
// K2_ERROR: Argument type mismatch: actual type is 'Int', but 'String & Int' was expected.
// K2_ERROR: The feature "when guards" is experimental and should be enabled explicitly. This can be done by supplying the compiler argument '-Xwhen-guards', but note that no stability guarantees are provided.
// K2_ERROR: Use of comma in 'when' condition with guard statement is not allowed.

private fun test(s: Any) {
    when (s) {
        is String,
        is Int <caret>if s > 5 -> { println("1") }
        else -> { println("2") }
    }
}
