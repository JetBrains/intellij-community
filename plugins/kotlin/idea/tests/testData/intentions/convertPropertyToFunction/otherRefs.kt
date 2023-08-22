// WITH_STDLIB
// AFTER-WARNING: Variable 't' is never used
package p

import p.foo

class A(val n: Int)

val A.<caret>foo: Boolean
    get() = n > 1

fun test() {
    val t = A::foo
}