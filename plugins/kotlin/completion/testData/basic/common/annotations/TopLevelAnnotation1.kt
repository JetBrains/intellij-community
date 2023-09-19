// FIR_IDENTICAL
// FIR_COMPARISON
package java

annotation class Hello
val v = 1

@<caret>

// INVOCATION_COUNT: 0
// EXIST: Hello
// EXIST_K2: Hello
// EXIST: Suppress
// EXIST_K2: Suppress
// ABSENT: String
// ABSENT_K2: String
// ABSENT: v
// ABSENT_K2: v
// EXIST: kotlin
// EXIST_K2: kotlin.
// ABSENT: collections
// ABSENT_K2: collections.
// ABSENT: io
// ABSENT_K2: io.
// ABSENT: lang
// ABSENT_K2: lang.
