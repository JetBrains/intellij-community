// "Add 'private' modifier" "false"
// ACTION: Convert to secondary constructor
// ACTION: Create test
// ACTION: Do not show return expression hints
// ACTION: Enable a trailing comma by default in the formatter
// ACTION: Move to class body

class My(val <caret>parameter: Int) {
    val other = parameter
}