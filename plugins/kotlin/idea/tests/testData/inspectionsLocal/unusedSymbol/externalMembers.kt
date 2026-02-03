// PROBLEM: none
// K2_ERROR: Modifier 'external' is not applicable to 'class'.
// ERROR: Modifier 'external' is not applicable to 'class'
open external class X {
    companion object {
        fun foo(<caret>x: Int)
    }
}