// "Create abstract function 'Foo.bar'" "false"
// ACTION: Create function 'bar'
// ACTION: Do not show return expression hints
// ACTION: Rename reference
// ERROR: Unresolved reference: bar

open class A

class Foo : A() {
    fun foo() {
        <caret>bar()
    }
}