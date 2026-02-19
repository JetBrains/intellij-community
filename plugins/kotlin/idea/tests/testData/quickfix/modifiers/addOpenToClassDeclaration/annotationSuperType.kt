// "Make 'A' open" "false"
// DISABLE_ERRORS
// ACTION: Introduce import alias
annotation class A

class AA : A<caret>