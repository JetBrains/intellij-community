// WITH_RUNTIME
// AFTER-WARNING: Parameter 'value' is never used

@set:Throws(RuntimeException::class)
var setter: String = ""
    set(value) = <caret>throw Exception()