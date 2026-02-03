// PROBLEM: [UNRESOLVED_REFERENCE] Unresolved reference 'Unresolved'.
// ERROR: Modifier 'data' is incompatible with 'inline'.
// ERROR: Modifier 'inline' is incompatible with 'data'.
// ERROR: Unresolved reference 'Unresolved'.
// IGNORE_K1
// ACTION: Create object 'Unresolved'
// ACTION: Create enum 'Unresolved'
// ACTION: Create class 'Unresolved'
// ACTION: Create annotation 'Unresolved'
// ACTION: Create interface 'Unresolved'
// FIX: none

data inline class Foo(val x: <caret>Unresolved)