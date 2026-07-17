// "Create abstract function 'foo'" "false"
// ERROR: Unresolved reference: foo
// K2_AFTER_ERROR: UNRESOLVED_REFERENCE
// K2_ERROR: UNRESOLVED_REFERENCE
class A {
    fun bar(b: Boolean) {}

    fun test() {
        bar(<caret>foo(1, "2"))
    }
}