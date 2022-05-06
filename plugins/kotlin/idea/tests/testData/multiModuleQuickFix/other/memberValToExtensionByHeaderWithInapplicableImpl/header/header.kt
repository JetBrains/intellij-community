// "Convert member to extension" "false"
// ACTION: Convert property to function
// ACTION: Do not show return expression hints
// ACTION: Move to companion object

expect class Foo {
    val <caret>foo: Int
}