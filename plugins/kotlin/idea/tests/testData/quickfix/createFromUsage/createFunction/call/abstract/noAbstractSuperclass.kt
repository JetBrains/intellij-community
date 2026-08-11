// "Create abstract function 'Foo.bar'" "false"
// ERROR: Unresolved reference: bar
// K2_AFTER_ERROR: UNRESOLVED_REFERENCE
// K2_ERROR: UNRESOLVED_REFERENCE

open class A

class Foo : A() {
    fun foo() {
        <caret>bar()
    }
}