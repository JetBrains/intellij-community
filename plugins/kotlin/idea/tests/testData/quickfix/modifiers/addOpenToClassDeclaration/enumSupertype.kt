// "Make 'E' open" "false"
// ERROR: This type is final, so it cannot be inherited from
// ERROR: Cannot access '<init>': it is private in 'E'
// ACTION: Introduce import alias
// ACTION: Make '<init>' internal
// ACTION: Make '<init>' public
// K2_AFTER_ERROR: FINAL_SUPERTYPE
// K2_AFTER_ERROR: INVISIBLE_REFERENCE
// K2_ERROR: FINAL_SUPERTYPE
// K2_ERROR: INVISIBLE_REFERENCE
enum class E {}
class A : E<caret>() {}
