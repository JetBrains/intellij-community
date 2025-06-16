// FIR_IDENTICAL
// IGNORE_K1
package a.b.c

sealed class SEALED {
    class AAAA: SEALED()
    object BBBB: SEALED()
    class CCCC: SEALED()
}

fun foo(e: SEALED) {
    when (e) {
        <caret>
    }
}

// WITH_ORDER
// EXIST: is SEALED.AAAA
// EXIST: SEALED.BBBB
// EXIST: SEALED
// EXIST: is SEALED.CCCC
// EXIST: { lookupString: "else -> "}
// EXIST: a.
// FIR_COMPARISON
