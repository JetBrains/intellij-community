// "Replace with 'arrayOf'" "true"
// K2_ERROR: Argument type mismatch: actual type is 'Array<Int>', but 'IntArray' was expected.
annotation class Ann(val x: IntArray)

fun test() {
    Ann([1, <caret>2, 3])
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ConvertCollectionLiteralToIntArrayOfFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ConvertCollectionLiteralToIntArrayOfFix