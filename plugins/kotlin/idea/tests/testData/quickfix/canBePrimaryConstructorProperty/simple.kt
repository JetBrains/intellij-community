// "Move to constructor" "true"
class Correct(name: String) {
    val <caret>name: String = name
}
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeinsight.intentions.MovePropertyToConstructorIntention