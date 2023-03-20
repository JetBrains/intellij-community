// "Make 'A' open" "false"
// DISABLE-ERRORS
// ACTION: Introduce import alias
annotation class A

class AA : A<caret>