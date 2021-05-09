// "Create member function 'Foo.test2'" "true"
// ERROR: Unresolved reference: test2
class Bar {

    fun test(foo: Foo) {
        foo.<caret>test2()
    }
}