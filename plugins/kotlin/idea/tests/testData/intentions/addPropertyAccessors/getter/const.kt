// IS_APPLICABLE: false
// ERROR: Const 'val' are only allowed on top level, in named objects, or in companion objects
class Test {
    const val x<caret> = 1
}