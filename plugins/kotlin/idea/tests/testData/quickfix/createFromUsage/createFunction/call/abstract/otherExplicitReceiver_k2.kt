// "Create abstract function 'foo'" "false"
// ACTION: Add 'b =' to argument
// ACTION: Create extension function 'B.foo'
// ACTION: Create member function 'B.foo'
// ACTION: Make 'open'
// ERROR: Unresolved reference: foo
abstract class A {
    fun bar(b: Boolean) {}

    fun test() {
        bar(B().<caret>foo(1, "2"))
    }
}

class B {

}

// IGNORE_K1