// "Make 'E' open" "false"
// ERROR: This type is final, so it cannot be inherited from
// ERROR: Cannot access '<init>': it is private in 'E'
// ACTION: Introduce import alias
// ACTION: Make '<init>' internal
// ACTION: Make '<init>' public
// K2_AFTER_ERROR: Cannot access 'constructor(): E': it is private in 'E'.
// K2_AFTER_ERROR: This type is final, so it cannot be extended.
enum class E {}
class A : E<caret>() {}
