// "Make 'Data' internal" "true"
// ACTION: Convert member to extension
// ACTION: Convert to block body
// ACTION: Make 'Data' internal
// ACTION: Make 'Data' public
// ACTION: Make 'bar' private
// ACTION: Move to companion object
// ACTION: Specify return type explicitly

private data class Data(val x: Int)

class First {
    internal fun <caret>bar(x: Int) = Data(x)
}
