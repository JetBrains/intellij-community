// PROBLEM: none
// WITH_RUNTIME
// COMPILER_ARGUMENTS: -opt-in=kotlin.RequiresOptIn

@RequiresOptIn
annotation class Marker

@Marker
class Foo

@OptIn(Marker::class)
fun makeFoo() = Foo()

@OptIn(<caret>Marker::class)
val foo = makeFoo()
