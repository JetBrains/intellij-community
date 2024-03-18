// "Create abstract function 'bar'" "false"
// ERROR: Unresolved reference: bar
class Foo : Runnable {
    override fun run() {
        <caret>bar()
    }
}