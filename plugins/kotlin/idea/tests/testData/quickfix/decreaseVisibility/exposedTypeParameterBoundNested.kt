// "Make 'PrivateInFileClass' private" "false"
// ACTION: Create test
// ACTION: Enable a trailing comma by default in the formatter
// ACTION: Import members from 'PrivateInFileClass'
// ACTION: Inline type parameter
// ACTION: Introduce import alias
// ACTION: Make 'PrivateInnerClass' 'open'
// ACTION: Make 'PrivateInnerClass' public
// ACTION: Remove final upper bound
// ERROR: 'private-in-file' generic exposes its 'private-in-class' parameter bound type PrivateInnerClass

private class PrivateInFileClass<T : <caret>PrivateInFileClass.PrivateInnerClass> {
    private class PrivateInnerClass
}
