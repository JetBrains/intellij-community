// FIR_IDENTICAL
// IGNORE_K1
enum class ENUM {
    AAAA, BBBB, CCCC
}

fun foo(e: ENUM?) {
    when (e) {
        <caret>
    }
}

// WITH_ORDER
// EXIST: ENUM.AAAA
// EXIST: ENUM.BBBB
// EXIST: ENUM.CCCC
// EXIST: null
// EXIST: { lookupString: "else -> "}
// EXIST: ENUM
// FIR_COMPARISON
