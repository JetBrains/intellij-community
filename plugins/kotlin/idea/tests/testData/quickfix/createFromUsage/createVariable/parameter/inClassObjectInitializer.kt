// "Create parameter 'foo'" "false"
// ERROR: Unresolved reference: foo
// K2_AFTER_ERROR: Unresolved reference 'foo'.

class A {
    companion object {
        init {
            val t: Int = <caret>foo
        }
    }
}