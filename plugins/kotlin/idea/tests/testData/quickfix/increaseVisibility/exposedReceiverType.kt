// "Make 'Private' protected" "true"
// ACTION: Convert receiver to parameter
// ACTION: Introduce import alias
// ACTION: Make 'Private' protected
// ACTION: Make 'Private' public

class Receiver<T>

abstract class My {
    private class Private

    abstract protected fun <caret>Receiver<Private>.foo()
}