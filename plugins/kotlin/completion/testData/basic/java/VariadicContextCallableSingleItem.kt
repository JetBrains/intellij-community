
// COMPILER_ARGUMENTS: -Xcontext-parameters

context(_: Int)
fun test() {
    context<caret>
}

// EXIST: {"lookupString":"context","tailText":"(a: A, ..., block: context(A, ...) () -> R) (kotlin)"}
// EXIST: contextOf
// EXIST: coroutineContext
// NOTHING_ELSE
