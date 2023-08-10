// "Import function 'Some.foobar'" "true"
// ERROR: Unresolved reference: foobar
package p2

import p1.Some.foobar

class A {
    fun some() {
        foobar<caret>()
    }
}