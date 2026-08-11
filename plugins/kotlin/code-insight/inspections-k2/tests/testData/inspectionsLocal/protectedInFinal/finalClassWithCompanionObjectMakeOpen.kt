// PROBLEM: 'protected' visibility is effectively 'private' in a final class
// FIX: Make class open
class C {
    companion object {
        <caret>protected fun foo() {}
    }
}
