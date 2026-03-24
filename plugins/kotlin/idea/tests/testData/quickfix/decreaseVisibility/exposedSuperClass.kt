// "Make 'First' private" "true"
// PRIORITY: HIGH
// ACTION: Add full qualifier
// ACTION: Add names to call arguments
// ACTION: Create test
// ACTION: Introduce import alias
// ACTION: Make 'Data' protected
// ACTION: Make 'Data' public
// ACTION: Make 'First' private
// K2_ERROR: 'protected (in Outer)' subclass exposes its 'private-in-class' supertype 'Data'.

class Outer {
    private open class Data(val x: Int)

    protected class First : <caret>Data(42)
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeVisibilityFix$ChangeToPrivateFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ChangeVisibilityFixFactories$ChangeToPrivateModCommandAction