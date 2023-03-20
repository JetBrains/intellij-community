// "Make 'PrivateInFileClass' private" "false"
// ACTION: Create test
// ACTION: Enable a trailing comma by default in the formatter
// ACTION: Import members from 'PrivateInFileClass'
// ACTION: Introduce import alias
// ACTION: Make 'PrivateInnerClass' public
// ERROR: 'private-in-file' generic exposes its 'private-in-class' parameter bound type PrivateInnerClass

private class PrivateInFileClass<T : <caret>PrivateInFileClass.PrivateInnerClass> {
    private class PrivateInnerClass
}
