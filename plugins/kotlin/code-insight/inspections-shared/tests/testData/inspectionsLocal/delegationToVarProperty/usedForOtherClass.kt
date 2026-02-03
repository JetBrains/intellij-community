// FIX: Change to 'val'
// NO_FIX: Remove 'var'
class Foo(<caret>var text: CharSequence) {
    inner class Bar: CharSequence by text
}