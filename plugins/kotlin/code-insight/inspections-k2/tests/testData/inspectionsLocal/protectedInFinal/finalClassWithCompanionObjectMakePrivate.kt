// PROBLEM: 'protected' visibility is effectively 'private' in a final class
// FIX: Make private
class C {
    companion object {
        <caret>protected fun foo() {}
    }
}
