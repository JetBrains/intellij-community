// WITH_RUNTIME
// // COMPILER_ARGUMENTS: -Xopt-in=kotlin.RequiresOptIn

@RequiresOptIn
annotation class Marker

class Foo(@property:Marker val x: Int, val y: Int)

@OptIn(<caret>Marker::class)
fun bar(foo: Foo): Int {
    return foo.y
}
