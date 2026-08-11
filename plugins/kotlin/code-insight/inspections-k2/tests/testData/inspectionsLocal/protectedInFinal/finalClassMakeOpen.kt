// PROBLEM: 'protected' visibility is effectively 'private' in a final class
// FIX: Make class open
class C {
    <caret>protected fun foo() {}
}
