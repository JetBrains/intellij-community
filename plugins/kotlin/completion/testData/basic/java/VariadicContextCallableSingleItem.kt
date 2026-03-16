// IGNORE_K1
// COMPILER_ARGUMENTS: -Xcontext-parameters

fun test() {
    context<caret>
}

// EXIST: {"lookupString":"context","tailText":"(a: A, ..., block: context(A, ...) () -> R) (kotlin)"}
// EXIST: contextOf
// EXIST: coroutineContext
// NOTHING_ELSE
