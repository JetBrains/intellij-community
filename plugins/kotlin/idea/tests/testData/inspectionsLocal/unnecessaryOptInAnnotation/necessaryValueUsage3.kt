// PROBLEM: none
// WITH_STDLIB
// COMPILER_ARGUMENTS: -opt-in=kotlin.RequiresOptIn

@RequiresOptIn
annotation class Marker

@Marker
class Foo

@OptIn(Marker::class)
var foo: Foo? = Foo()

@OptIn(<caret>Marker::class)
fun f() {
    foo = null
}
