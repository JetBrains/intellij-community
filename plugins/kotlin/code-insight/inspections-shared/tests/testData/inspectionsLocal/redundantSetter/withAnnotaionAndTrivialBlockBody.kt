// PROBLEM: Redundant setter body
// FIX: Remove redundant setter body
class Foo {
    var foo: String = ""
        @Deprecated("") <caret>set(x) {
            field = x
        }
}