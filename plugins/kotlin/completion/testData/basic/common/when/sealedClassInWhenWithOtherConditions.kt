// FIR_IDENTICAL

sealed class SEALED
class AAAA: SEALED()
object BBBB: SEALED()
class CCCC: SEALED()

fun foo(e: SEALED) {
    when (e) {
        is AAAA, CCCC -> TODO()
        <caret>
    }
}

// WITH_ORDER
// EXIST: BBBB
// EXIST: { lookupString: "else -> "}
// EXIST: SEALED
// FIR_COMPARISON
