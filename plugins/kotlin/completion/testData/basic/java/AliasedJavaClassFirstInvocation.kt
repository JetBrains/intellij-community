// FIR_IDENTICAL
// FIR_COMPARISON
val x = StringBuilde<caret>

// INVOCATION_COUNT: 1
// EXIST: { lookupString:"StringBuilder", tailText:" (kotlin.text)"}
// ABSENT: { lookupString:"StringBuilder", tailText:" (java.lang)"}
