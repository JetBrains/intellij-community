// "Create abstract function 'bar'" "false"
// ACTION: Create function 'bar'
// ERROR: Unresolved reference: bar
class Foo : Runnable {
    override fun run() {
        <caret>bar()
    }
}

// IGNORE_K1