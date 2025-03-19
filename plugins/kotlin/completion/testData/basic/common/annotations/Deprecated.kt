// FIR_COMPARISON
// FIR_IDENTICAL

@Dep<caret>
fun foo() { }

// INVOCATION_COUNT: 1
// EXIST: { itemText: "Deprecated", tailText: " (kotlin)" }
// EXIST: { itemText: "DeprecatedSinceKotlin", tailText: " (kotlin)" }
// EXIST_NATIVE_ONLY: { itemText: "FreezingIsDeprecated", tailText: " (kotlin.native)" }
// EXIST_COMMON_ONLY: { itemText: "FreezingIsDeprecated", tailText: " (kotlin.native)" }
// NOTHING_ELSE
