// "Make 'Data' public" "true"
// PRIORITY: HIGH

class Other {
    private open class Data(val x: Int)
}

class Another {
    protected class First : Other.<caret>Data(42)
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeVisibilityFix$ChangeToPublicFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ChangeVisibilityFixFactories$ChangeToPublicModCommandAction