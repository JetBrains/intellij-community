// "Remove parameter 'value'" "false"
// ACTION: Compiler warning 'UNUSED_PARAMETER' options
// ACTION: Enable a trailing comma by default in the formatter
// ACTION: Enable option 'Function parameter types' for 'Types' inlay hints
// ACTION: Specify type explicitly
class Abacaba {
    var foo: String
        get() = ""
        set(<caret>value) {}
}