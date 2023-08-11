// PROBLEM: none
// ERROR: Modifier 'data' is incompatible with 'inline'
// ERROR: Modifier 'inline' is incompatible with 'data'
// ERROR: Unresolved reference: Unresolved
// IGNORE_FIR

data inline class Foo(val x: <caret>Unresolved)