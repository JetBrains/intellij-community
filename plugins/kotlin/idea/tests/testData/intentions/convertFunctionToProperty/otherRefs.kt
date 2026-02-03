// WITH_STDLIB
// AFTER-WARNING: Variable 't' is never used
// PRIORITY: LOW
package p

import p.foo

class A(val n: Int)

fun A.<caret>foo(): Boolean = n > 1

fun test() {
    val t = A::foo
}