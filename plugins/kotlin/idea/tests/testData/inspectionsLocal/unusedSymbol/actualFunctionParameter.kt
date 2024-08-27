// PROBLEM: none

//common
expect fun op(expectParameter: String)

//platform
actual fun op(actual<caret>Parameter: String) {}


//common
expect class CtrParams23(expectClass: String) {}

//platform
actual class CtrParams23 actual constructor(actualClass: String) {}

