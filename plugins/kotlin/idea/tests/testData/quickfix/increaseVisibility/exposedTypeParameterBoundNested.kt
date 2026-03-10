// "Make 'PrivateInnerClass' public" "true"
// PRIORITY: HIGH
// ACTION: Create test
// ACTION: Enable a trailing comma by default in the formatter
// ACTION: Import members from 'PrivateInFileClass'
// ACTION: Inline type parameter
// ACTION: Introduce import alias
// ACTION: Make 'PrivateInnerClass' 'open'
// ACTION: Make 'PrivateInnerClass' public
// ACTION: Remove final upper bound
// K2_ERROR: 'private-in-file' generic exposes its 'private-in-class' parameter bound type 'PrivateInnerClass'.

private class PrivateInFileClass<T : <caret>PrivateInFileClass.PrivateInnerClass> {
    private class PrivateInnerClass
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeVisibilityFix$ChangeToPublicFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ChangeVisibilityFixFactories$ChangeToPublicModCommandAction