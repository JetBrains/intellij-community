// "Create parameter 'foo'" "false"
// ERROR: Unresolved reference: foo

class A<T> {
    val <T> T.test: T get() {
        return <caret>foo
    }
}