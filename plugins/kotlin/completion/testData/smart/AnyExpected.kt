fun foo(){}

class C{}

val v: Any = <caret>

// ABSENT: ::foo
// ABSENT: ::C
// ABSENT: ::Runnable
// ABSENT: C

// IGNORE_K2
