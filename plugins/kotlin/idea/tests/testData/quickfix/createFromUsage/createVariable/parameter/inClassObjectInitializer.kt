// "Create parameter 'foo'" "false"
// ERROR: Unresolved reference: foo

class A {
    companion object {
        init {
            val t: Int = <caret>foo
        }
    }
}