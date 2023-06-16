// FIR_COMPARISON
// FIR_IDENTICAL
typealias MyInt = Int

fun MyInt.foo(n: MyInt): MyInt = 1

fun Int.test() {
    fo<caret>
}

// EXIST: {"lookupString":"foo","tailText": "(n: MyInt /* = Int */) for MyInt /* = Int */ in <root>","typeText":"MyInt /* = Int */"}