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
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeVisibilityFix$ChangeToProtectedFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ChangeVisibilityFixFactories$ChangeToProtectedModCommandAction