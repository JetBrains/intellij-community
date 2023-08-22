// "Move to constructor" "true"
class Container(index: Int) {
    protected open var <caret>index = index
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.intentions.MovePropertyToConstructorIntention