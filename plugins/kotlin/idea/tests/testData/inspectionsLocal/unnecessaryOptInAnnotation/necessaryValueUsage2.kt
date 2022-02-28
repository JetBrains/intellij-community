// PROBLEM: none
// WITH_STDLIB
// COMPILER_ARGUMENTS: -opt-in=kotlin.RequiresOptIn

@RequiresOptIn
annotation class Marker

@Marker
class Foo {
    fun f() {}
}

@OptIn(Marker::class)
val foo = Foo()

@OptIn(<caret>Marker::class)
fun bar() {
    val x = foo
}
