// "Create parameter 'foo'" "false"
// ERROR: Unresolved reference: foo
// K2_AFTER_ERROR: Unresolved reference 'foo'.

class A {
    val <T> T.test: T get() {
        return <caret>foo
    }
}