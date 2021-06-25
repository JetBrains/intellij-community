// PROBLEM: none
fun test(x:X, y:Y) {
    if (<caret>x::class == y::class) {}
}
open class X
class Y:X()