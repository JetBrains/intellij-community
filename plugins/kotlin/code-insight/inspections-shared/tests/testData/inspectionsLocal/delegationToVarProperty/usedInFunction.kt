// FIX: Change to 'val'
class Foo(<caret>var text: CharSequence): CharSequence by text {
    fun bar() {
        text
    }
}