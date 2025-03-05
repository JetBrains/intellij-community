// "Move to constructor" "true"
class Correct(name: String) {
    val <caret>name: String = name
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.intentions.MovePropertyToConstructorIntention
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.intentions.MovePropertyToConstructorIntention