// FIR_IDENTICAL
// FIR_COMPARISON

sealed class SEALED
class AAAA: SEALED()
object BBBB: SEALED()
abstract class CCCC: SEALED()

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
// EXIST: is SomeClass
