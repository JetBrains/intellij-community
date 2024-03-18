// "Create abstract function 'Foo.bar'" "false"
// ERROR: Unresolved reference: bar

open class A

class Foo : A() {
    fun foo() {
        <caret>bar()
    }
}