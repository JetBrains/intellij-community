// FIR_IDENTICAL
// IGNORE_K1
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
// FIR_COMPARISON
