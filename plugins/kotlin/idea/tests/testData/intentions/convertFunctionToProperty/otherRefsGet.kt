// WITH_STDLIB
// AFTER-WARNING: Variable 't' is never used
package p

import p.getFoo

class A(val n: Int)

fun A.<caret>getFoo(): Boolean = n > 1

fun test() {
    val t = A::getFoo
}