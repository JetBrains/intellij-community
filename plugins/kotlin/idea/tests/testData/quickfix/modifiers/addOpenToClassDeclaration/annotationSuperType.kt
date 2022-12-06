// "Make 'A' open" "false"
// DISABLE-ERRORS
// ACTION: Change to constructor invocation
// ACTION: Introduce import alias
annotation class A

class AA : A<caret>