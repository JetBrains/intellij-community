// "Make 'First' private" "true"
// ACTION: Add import for 'Other.Data'
// ACTION: Add names to call arguments
// ACTION: Introduce import alias
// ACTION: Make 'Data' public
// ACTION: Make 'First' private

class Other {
    internal open class Data(val x: Int)
}

class Another {
    protected class First : Other.<caret>Data(42)
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeVisibilityFix$ChangeToPrivateFix