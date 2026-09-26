// FIR_IDENTICAL

enum class ENUM {
    AAAA, BBBB, CCCC
}

fun foo(e: ENUM) {
    when (e) {
        ENUM.AAAA, ENUM.CCCC -> TODO()
        <caret>
    }
}

// WITH_ORDER
// EXIST: ENUM.BBBB
// EXIST: { lookupString: "else -> "}
// EXIST: ENUM
// FIR_COMPARISON
