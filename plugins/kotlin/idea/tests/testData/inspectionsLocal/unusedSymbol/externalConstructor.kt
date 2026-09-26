// PROBLEM: none
// ERROR: Modifier 'external' is not applicable to 'class'
// K2_ERROR: WRONG_MODIFIER_TARGET
open external class X {
    constructor(<caret>x: Int)
}