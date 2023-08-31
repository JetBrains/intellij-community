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

// WITH_ORDER
// EXIST: ENUM.BBBB
// FIR_COMPARISON
