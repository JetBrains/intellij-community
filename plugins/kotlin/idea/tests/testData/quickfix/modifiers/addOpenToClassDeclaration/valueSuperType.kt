// "Make 'V' open" "false"
// DISABLE-ERRORS
// ACTION: Add constructor parameters from V(Int)
// ACTION: Change to constructor invocation
// ACTION: Introduce import alias
@JvmInline
value class V(val x: Int)

class VV : V<caret>