package test

class SomeType

val anonymous = fun(): ST<caret> {}

// FIR_COMPARISON
// FIR_IDENTICAL
// ELEMENT: SomeType