// "Move to constructor" "true"
class A(vararg strings: String) {
    val <caret>strings = strings
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.intentions.MovePropertyToConstructorIntention
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.intentions.MovePropertyToConstructorIntention