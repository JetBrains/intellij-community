// "Create abstract function 'bar'" "false"
// ACTION: Create function 'bar'
// ACTION: Do not show return expression hints
// ACTION: Rename reference
// ERROR: Unresolved reference: bar
class Foo : Runnable {
    override fun run() {
        <caret>bar()
    }
}