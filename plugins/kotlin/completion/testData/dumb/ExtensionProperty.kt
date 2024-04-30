val a = 3.prefix<caret>

val Int.prefixTest
    get() = 5


// EXIST: prefixTest
// NOTHING_ELSE