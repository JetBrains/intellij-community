// "Make 'E' 'open'" "false"
// K2_ERROR: FINAL_SUPERTYPE
// K2_AFTER_ERROR: FINAL_SUPERTYPE
enum class E { X, Y, Z; }
class A : E<caret> {}
