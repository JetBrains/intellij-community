// PROBLEM: none

//common
expect fun op(expectParameter: String)

//platform
actual fun op(actualParameter: String) {}


//common
expect class CtrParams23(expectClass: String) {}

//platform
actual class CtrParams23 actual constructor(actual<caret>Class: String) {}

