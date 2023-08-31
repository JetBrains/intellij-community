// FIR_IDENTICAL
// IGNORE_K1
enum class ENUM {
    AAAA, BBBB, CCCC
}

fun foo(e: ENUM) {
    when (e) {
        ENUM.AAAA, ENUM.CCCC, <caret> -> TODO()

    }
}

// EXIST: ENUM.BBBB
// NOTHING_ELSE
// FIR_COMPARISON
