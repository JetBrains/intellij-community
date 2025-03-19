// WITH_STDLIB
// PROBLEM: none

fun runCatching(f: () -> Int): Result<Int> = Result.success(f())

fun foo() = runCatching<caret> { 42 }.getOrThrow()
