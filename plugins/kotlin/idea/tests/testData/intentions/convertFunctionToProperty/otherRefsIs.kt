// WITH_STDLIB
// AFTER-WARNING: Variable 't' is never used
// PRIORITY: LOW
package p

import p.isFoo

class A(val n: Int)

fun A.<caret>isFoo(): Boolean = n > 1

fun test() {
    val t = A::isFoo
}