// "'protected' visibility is effectively 'private' in a final class" "true"
// FIX: "Make private"

annotation class A

@A
class Test {
    <caret>protected fun foo() {
    }
}
