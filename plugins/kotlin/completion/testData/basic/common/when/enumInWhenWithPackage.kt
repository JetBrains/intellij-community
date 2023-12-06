// FIR_IDENTICAL
// IGNORE_K1
package a.b.c

enum class ENUM {
    AAAA, BBBB, CCCC
}

fun foo(e: ENUM) {
    when (e) {
        <caret>
    }
}

// WITH_ORDER
// EXIST: ENUM.AAAA
// EXIST: ENUM.BBBB
// EXIST: ENUM.CCCC
// EXIST: { lookupString: "else -> "}
// FIR_COMPARISON
