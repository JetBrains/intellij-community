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

// EXIST: ENUM.AAAA
// EXIST: ENUM.BBBB
// EXIST: ENUM.CCCC
// EXIST: { lookupString: "else -> "}
// EXIST: null
// NOTHING_ELSE
// FIR_COMPARISON
