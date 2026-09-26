// "Make 'A' 'open'" "false"
// K2_ERROR: FINAL_SUPERTYPE
// K2_AFTER_ERROR: FINAL_SUPERTYPE
data class A(val x: Int)
class B: A<caret>(42)