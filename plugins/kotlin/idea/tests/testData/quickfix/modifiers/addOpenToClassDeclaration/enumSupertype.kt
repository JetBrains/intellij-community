// "Make 'E' open" "false"
// ERROR: This type is final, so it cannot be inherited from
// ERROR: Cannot access '<init>': it is private in 'E'
// ACTION: Introduce import alias
// ACTION: Make '<init>' internal
// ACTION: Make '<init>' public
enum class E {}
class A : E<caret>() {}
