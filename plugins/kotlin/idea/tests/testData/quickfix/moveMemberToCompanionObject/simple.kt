// "Move to companion object" "true"
// K2_ERROR: Const 'val' is only allowed on top level, in named objects, in companion objects or companion blocks.
class Test {
    <caret>const val foo = ""
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.intentions.MoveMemberToCompanionObjectIntention
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeinsight.intentions.MoveMemberToCompanionObjectIntention