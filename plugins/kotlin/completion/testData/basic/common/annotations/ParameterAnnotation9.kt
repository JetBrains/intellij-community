// FIR_IDENTICAL
// FIR_COMPARISON
annotation class SHello
val v = 1

fun foo(@[S<caret>) { }

// INVOCATION_COUNT: 1
// EXIST: SHello
// EXIST: Suppress
// ABSENT: String
// EXIST_NATIVE_ONLY: { lookupString:"String", tailText: " (kotlinx.cinterop.internal.ConstantValue)" }
// ABSENT: v
