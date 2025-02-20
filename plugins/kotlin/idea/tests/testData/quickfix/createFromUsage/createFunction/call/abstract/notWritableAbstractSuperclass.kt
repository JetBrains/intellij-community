// "Create abstract function 'bar'" "false"
// ERROR: Unresolved reference: bar
// K2_AFTER_ERROR: Unresolved reference 'bar'.
class Foo : Runnable {
    override fun run() {
        <caret>bar()
    }
}