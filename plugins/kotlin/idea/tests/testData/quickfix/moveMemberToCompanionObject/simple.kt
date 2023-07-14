// "Move to companion object" "true"
class Test {
    <caret>const val foo = ""
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.intentions.MoveMemberToCompanionObjectIntention