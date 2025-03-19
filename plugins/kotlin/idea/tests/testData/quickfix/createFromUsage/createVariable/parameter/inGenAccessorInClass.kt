// "Create parameter 'foo'" "false"
// ERROR: Unresolved reference: foo

class A {
    val <T> T.test: T get() {
        return <caret>foo
    }
}