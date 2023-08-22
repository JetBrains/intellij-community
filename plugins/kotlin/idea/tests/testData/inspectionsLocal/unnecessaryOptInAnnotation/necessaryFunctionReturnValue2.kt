// PROBLEM: none
// WITH_STDLIB
// COMPILER_ARGUMENTS: -opt-in=kotlin.RequiresOptIn

@RequiresOptIn
annotation class Marker

@Marker
class Foo

@OptIn(Marker::class)
fun makeFoo(): Foo? = null

@OptIn(<caret>Marker::class)
val foo = makeFoo()
