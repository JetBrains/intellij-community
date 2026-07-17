// "Move to companion object" "true"
// K2_ERROR: CONST_VAL_NOT_TOP_LEVEL_OR_OBJECT
class Test {
    <caret>const val foo = ""
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.intentions.MoveMemberToCompanionObjectIntention
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeinsight.intentions.MoveMemberToCompanionObjectIntention