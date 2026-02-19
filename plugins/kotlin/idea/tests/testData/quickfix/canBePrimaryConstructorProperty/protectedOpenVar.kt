// "Move to constructor" "true"
class Container(index: Int) {
    protected open var <caret>index = index
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.intentions.MovePropertyToConstructorIntention
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.intentions.MovePropertyToConstructorIntention