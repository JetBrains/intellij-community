// WITH_STDLIB
// IGNORE_K1
// K2_ERROR: The feature "when guards" is experimental and should be enabled explicitly. This can be done by supplying the compiler argument '-Xwhen-guards', but note that no stability guarantees are provided.

private fun test(s: Any) {
    // 1
    when<caret> (s) { // 2
        is String -> println("1") // 3
        // 4
        is Int if (s > 5 || s < 3) -> { // 5
            println("2") // 6
        } // 7
        // 8
        else -> { println("3") } // 9
    }
}
