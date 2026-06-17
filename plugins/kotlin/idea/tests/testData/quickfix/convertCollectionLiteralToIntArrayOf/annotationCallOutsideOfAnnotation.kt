// "Replace with 'arrayOf'" "true"
// K2_ERROR: Argument type mismatch: actual type is 'Array<Int>', but 'IntArray' was expected.
// K2_ERROR: Array literals outside of annotations are unsupported.
// K2_ERROR: The feature "collection literals" is experimental and should be enabled explicitly. This can be done by supplying the compiler argument '-Xcollection-literals', but note that no stability guarantees are provided.
annotation class Ann(val x: IntArray)

fun test() {
    Ann([1, <caret>2, 3])
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ConvertCollectionLiteralToIntArrayOfFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ConvertCollectionLiteralToIntArrayOfFix