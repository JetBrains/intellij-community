// "Remove parameter 'value'" "false"
// ACTION: Compiler warning 'UNUSED_PARAMETER' options
// ACTION: Enable 'Types' inlay hints
// ACTION: Enable a trailing comma by default in the formatter
// ACTION: Specify type explicitly
class Abacaba {
    var foo: String
        get() = ""
        set(<caret>value) {}
}