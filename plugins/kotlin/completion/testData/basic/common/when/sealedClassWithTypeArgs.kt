// FIR_IDENTICAL
// IGNORE_K1
sealed class SEALED
class AAAA<E, S>: SEALED()
object BBBB: SEALED()
class CCCC<E>: SEALED()

fun foo(e: SEALED) {
    when (e) {
        <caret>
    }
}

// WITH_ORDER
// EXIST: { lookupString: "is AAAA", tailText: "<*, *> -> " }
// EXIST: BBBB
// EXIST: { lookupString: "is CCCC", tailText: "<*> -> " }
// EXIST: { lookupString: "else -> "}
// FIR_COMPARISON
