// WITH_STDLIB
// PROBLEM: none

class Wrapper<T>(val value: T)

fun star(): Wrapper<Wrapper<*>> = Wrapper(Wrapper(Any()))

fun star2<caret>(): List<*> = listOf(Any())