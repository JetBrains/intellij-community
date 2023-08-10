// "Make 'Data' public" "true"

class Other {
    private open class Data(val x: Int)
}

class Another {
    protected class First : Other.<caret>Data(42)
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeVisibilityFix$ChangeToPublicFix