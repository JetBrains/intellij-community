// FIR_COMPARISON
// FIR_IDENTICAL
typealias MyInt = Int

fun MyInt.test() {
    thi<caret>
}

// EXIST: {"lookupString":"this", "typeText":"MyInt /* = Int */"}