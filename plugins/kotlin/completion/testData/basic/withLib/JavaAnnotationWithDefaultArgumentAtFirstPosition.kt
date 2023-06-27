// FIR_COMPARISON
// FIR_IDENTICAL

const val s = ""

@Anno(<caret>)
class Foo


// EXIST: "value ="
// EXIST: "foo ="
// EXIST: s