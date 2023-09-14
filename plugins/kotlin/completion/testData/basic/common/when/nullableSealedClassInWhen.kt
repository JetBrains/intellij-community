// FIR_IDENTICAL
// IGNORE_K1
sealed class SEALED
class AAAA: SEALED()
object BBBB: SEALED()
class CCCC: SEALED()

fun foo(e: SEALED?) {
    when (e) {
        <caret>
    }
}

// WITH_ORDER
// EXIST: null
// EXIST: is AAAA
// EXIST: BBBB
// EXIST: is CCCC
// EXIST: { lookupString: "else -> "}
// FIR_COMPARISON
