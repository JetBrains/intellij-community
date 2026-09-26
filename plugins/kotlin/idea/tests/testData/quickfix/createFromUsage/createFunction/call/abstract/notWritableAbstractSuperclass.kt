// "Create abstract function 'bar'" "false"
// ERROR: Unresolved reference: bar
// K2_AFTER_ERROR: UNRESOLVED_REFERENCE
// K2_ERROR: UNRESOLVED_REFERENCE
class Foo : Runnable {
    override fun run() {
        <caret>bar()
    }
}