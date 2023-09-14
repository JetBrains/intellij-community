// FIR_IDENTICAL
// IGNORE_K1
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
// FIR_COMPARISON
