// IS_APPLICABLE: false
// ERROR: Const 'val' are only allowed on top level, in named objects, or in companion objects
// K2_ERROR: CONST_VAL_NOT_TOP_LEVEL_OR_OBJECT
class Test {
    const val x<caret> = 1
}