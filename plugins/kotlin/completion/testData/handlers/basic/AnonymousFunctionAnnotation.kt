package test

annotation class SomeAnnotation

val anonymous = @SA<caret> fun() {}

// FIR_COMPARISON
// FIR_IDENTICAL
// ELEMENT: SomeAnnotation