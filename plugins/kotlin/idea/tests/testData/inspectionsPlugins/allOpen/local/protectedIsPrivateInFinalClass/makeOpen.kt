// "'protected' visibility is effectively 'private' in a final class" "true"
// FIX: "Make class open"

class Test {
    <caret>protected fun foo() {
    }
}
