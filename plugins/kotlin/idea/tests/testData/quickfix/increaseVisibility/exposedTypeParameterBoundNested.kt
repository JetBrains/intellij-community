// "Make 'PrivateInnerClass' public" "true"
// ACTION: Create test
// ACTION: Do not show return expression hints
// ACTION: Enable a trailing comma by default in the formatter
// ACTION: Import members from 'PrivateInFileClass'
// ACTION: Introduce import alias
// ACTION: Make 'PrivateInnerClass' public

private class PrivateInFileClass<T : <caret>PrivateInFileClass.PrivateInnerClass> {
    private class PrivateInnerClass
}
