// "Make 'V' open" "false"
// DISABLE_ERRORS
// ACTION: Introduce import alias
@JvmInline
value class V(val x: Int)

class VV : V<caret>