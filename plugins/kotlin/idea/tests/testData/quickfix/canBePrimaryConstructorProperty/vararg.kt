// "Move to constructor" "true"
class A(vararg strings: String) {
    val <caret>strings = strings
}
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeinsight.intentions.MovePropertyToConstructorIntention