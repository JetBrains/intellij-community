// "Make 'First' private" "true"
// ACTION: Add full qualifier
// ACTION: Add names to call arguments
// ACTION: Create test
// ACTION: Introduce import alias
// ACTION: Make 'Data' protected
// ACTION: Make 'Data' public
// ACTION: Make 'First' private

class Outer {
    private open class Data(val x: Int)

    protected class First : <caret>Data(42)
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeVisibilityFix$ChangeToPrivateFix