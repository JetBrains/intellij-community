// PROBLEM: none
class Foo(<caret>var text: CharSequence) {
    inner class Bar: CharSequence by text
}
