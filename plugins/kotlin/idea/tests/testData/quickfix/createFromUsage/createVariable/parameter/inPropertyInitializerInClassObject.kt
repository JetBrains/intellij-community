// "Create parameter 'foo'" "false"
// ERROR: Unresolved reference: foo

class A {
    companion object {
        val test: Int = <caret>foo
    }
}