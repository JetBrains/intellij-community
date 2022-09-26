// "Import function 'Some.foobar'" "true"
// ERROR: Unresolved reference: foobar
/* IGNORE_FIR */
package p2

import p1.Some.foobar

class A {
    fun some() {
        foobar<caret>()
    }
}