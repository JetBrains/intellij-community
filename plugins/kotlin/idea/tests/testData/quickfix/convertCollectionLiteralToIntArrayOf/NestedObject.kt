// "Replace with 'arrayOf'" "true"
annotation class Ann(val x: IntArray = [1, 2, 3]) {
    object Nested {
        val y1: IntArray = [
            1,<caret>
            2, // comment
            3
        ]
    }
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ConvertCollectionLiteralToIntArrayOfFix