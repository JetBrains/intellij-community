// PROBLEM: 'protected' visibility is effectively 'private' in a final class
// FIX: Make private
class C {
    <caret>protected fun foo() {}
}
