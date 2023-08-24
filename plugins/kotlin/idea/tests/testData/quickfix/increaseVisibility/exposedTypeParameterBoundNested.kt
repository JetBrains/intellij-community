// "Make 'PrivateInnerClass' public" "true"
// ACTION: Create test
// ACTION: Enable a trailing comma by default in the formatter
// ACTION: Import members from 'PrivateInFileClass'
// ACTION: Introduce import alias
// ACTION: Make 'PrivateInnerClass' public

private class PrivateInFileClass<T : <caret>PrivateInFileClass.PrivateInnerClass> {
    private class PrivateInnerClass
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeVisibilityFix$ChangeToPublicFix