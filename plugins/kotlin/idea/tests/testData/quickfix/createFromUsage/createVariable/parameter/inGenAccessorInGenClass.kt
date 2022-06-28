// "Create parameter 'foo'" "false"
// ACTION: Create extension property 'T.foo'
// ACTION: Create local variable 'foo'
// ACTION: Create property 'foo'
// ACTION: Do not show return expression hints
// ACTION: Rename reference
// ERROR: Unresolved reference: foo

class A<T> {
    val <T> T.test: T get() {
        return <caret>foo
    }
}