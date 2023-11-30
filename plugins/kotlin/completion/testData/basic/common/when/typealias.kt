// IGNORE_K1
// FIR_COMPARISON
// FIR_IDENTICAL
sealed class SS

class AA: SS()
class BB: SS()

typealias TT = AA

fun test(ss: SS) = when (ss) {
    is TT -> {}
    <caret>
}

// EXIST: is BB
// ABSENT: is AA