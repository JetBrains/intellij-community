// KT-1968 Double closing parentheses entered when completing unit function
// FIR_COMPARISON
// FIR_IDENTICAL
package some

fun test() = 12
fun test1() {}

val a = <caret>

// ELEMENT: test