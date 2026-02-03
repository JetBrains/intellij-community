// "Import function 'Some.foobar'" "true"
// ERROR: Unresolved reference: foobar
package p2

class A {
    fun some() {
        foobar<caret>()
    }
}