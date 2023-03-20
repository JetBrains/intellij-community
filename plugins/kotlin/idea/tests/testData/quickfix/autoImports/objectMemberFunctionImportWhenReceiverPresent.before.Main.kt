// "Import function 'Some.foobar'" "true"
// ERROR: Unresolved reference: foobar
/* IGNORE_FIR */
package p2

class A {
    fun some() {
        foobar<caret>()
    }
}