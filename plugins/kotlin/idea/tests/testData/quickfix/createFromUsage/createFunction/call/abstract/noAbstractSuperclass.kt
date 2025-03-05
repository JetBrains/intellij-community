// "Create abstract function 'Foo.bar'" "false"
// ERROR: Unresolved reference: bar
// K2_AFTER_ERROR: Unresolved reference 'bar'.

open class A

class Foo : A() {
    fun foo() {
        <caret>bar()
    }
}