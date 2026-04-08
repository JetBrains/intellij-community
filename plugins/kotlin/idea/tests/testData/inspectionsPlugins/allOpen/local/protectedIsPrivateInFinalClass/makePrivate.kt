// "'protected' visibility is effectively 'private' in a final class" "true"
// FIX: "Make private"

class Test {
    <caret>protected fun foo() {
    }
}
