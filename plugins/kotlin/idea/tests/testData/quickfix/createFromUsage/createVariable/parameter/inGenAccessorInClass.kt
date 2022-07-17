// "Create parameter 'foo'" "false"
// ACTION: Create extension property 'T.foo'
// ACTION: Create local variable 'foo'
// ACTION: Create property 'foo'
// ACTION: Rename reference
// ERROR: Unresolved reference: foo

class A {
    val <T> T.test: T get() {
        return <caret>foo
    }
}