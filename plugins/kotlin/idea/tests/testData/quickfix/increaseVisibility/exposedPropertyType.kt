// "Make 'Data' public" "true"
// ACTION: Add getter
// ACTION: Convert property initializer to getter
// ACTION: Convert property to function
// ACTION: Convert to lazy property
// ACTION: Introduce backing property
// ACTION: Make 'Data' public
// ACTION: Make 'foo' private
// ACTION: Move to companion object
// ACTION: Move to constructor
// ACTION: Specify type explicitly

private data class Data(val x: Int)

class First {
    val <caret>foo = Data(13)
}
