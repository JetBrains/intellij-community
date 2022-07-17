// PROBLEM: none
// WITH_STDLIB
// COMPILER_ARGUMENTS: -opt-in=kotlin.RequiresOptIn

@RequiresOptIn
annotation class Marker

@Marker
class Foo

@OptIn(Marker::class)
fun makeFoo(action: (Int) -> List<Foo>): Int = 0

@OptIn(<caret>Marker::class)
val foo = makeFoo { emptyList() }
