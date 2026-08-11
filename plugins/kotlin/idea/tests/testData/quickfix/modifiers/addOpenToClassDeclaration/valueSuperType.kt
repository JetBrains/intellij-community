// "Make 'V' 'open'" "false"
// K2_ERROR: FINAL_SUPERTYPE
// K2_AFTER_ERROR: FINAL_SUPERTYPE
@JvmInline
value class V(val x: Int)

class VV() : V(42)<caret>