// FIR_IDENTICAL

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
// EXIST: ENUM
// FIR_COMPARISON
