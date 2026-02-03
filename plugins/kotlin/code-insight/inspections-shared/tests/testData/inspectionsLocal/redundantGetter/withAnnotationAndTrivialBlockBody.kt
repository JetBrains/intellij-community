// PROBLEM: Redundant getter body
// FIX: Remove redundant getter body
class Foo {
    val foo: String = ""
        @Deprecated("") <caret>get() {
            return field
        }
}