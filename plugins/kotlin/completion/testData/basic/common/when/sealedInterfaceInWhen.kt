// FIR_IDENTICAL
// IGNORE_K1
sealed interface SEALED
class AAAA: SEALED()
object BBBB: SEALED()
class CCCC: SEALED()

class SomeClass

fun foo(e: SEALED) {
    when (e) {
        <caret>
    }
}

// EXIST: is AAAA
// EXIST: BBBB
// EXIST: is CCCC
// EXIST: SEALED
// EXIST: { lookupString: "else -> "}
// FIR_COMPARISON
