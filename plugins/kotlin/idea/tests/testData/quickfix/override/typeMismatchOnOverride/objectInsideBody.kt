// "Change type to 'Int'" "true"
// K2_ACTION: "Specify 'Int' type" "true"
// ERROR: Null can not be a value of a non-null type Int
// K2_AFTER_ERROR: NULL_FOR_NONNULL_TYPE
// K2_ERROR: PROPERTY_TYPE_MISMATCH_ON_OVERRIDE
interface Test<T> {
    val prop : T
}

class Other {
    fun doTest() {
        val some = object: Test<Int> {
            override val <caret>prop = null
        }
    }
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeVariableTypeFix$OnType
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ChangeTypeQuickFixFactories$UpdateTypeQuickFix