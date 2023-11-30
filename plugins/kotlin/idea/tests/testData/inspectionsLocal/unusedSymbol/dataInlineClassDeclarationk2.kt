// PROBLEM: [UNRESOLVED_REFERENCE] Unresolved reference 'Unresolved'.
// ERROR: Modifier 'data' is incompatible with 'inline'.
// ERROR: Modifier 'inline' is incompatible with 'data'.
// ERROR: Unresolved reference 'Unresolved'.
// IGNORE_K1
// FIX: none

data inline class Foo(val x: <caret>Unresolved)