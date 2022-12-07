// "Make 'VV' open" "false"
// DISABLE-ERRORS
// ACTION: Introduce import alias
@JvmInline
value class V(val x: Int)

class VV : V<caret>