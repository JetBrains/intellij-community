// "Create abstract function 'foo'" "false"
// ERROR: Unresolved reference: foo
// K2_AFTER_ERROR: UNRESOLVED_REFERENCE
// K2_ERROR: UNRESOLVED_REFERENCE
abstract class A {
    fun bar(b: Boolean) {}

    fun test() {
        bar(B().<caret>foo(1, "2"))
    }
}

class B {

}