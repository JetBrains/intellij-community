// FIR_COMPARISON
// FIR_IDENTICAL
@Deprecate<caret>
fun foo() { }

// INVOCATION_COUNT: 2
// WITH_ORDER
// EXIST: { itemText: "Deprecated", tailText: " (kotlin)" }
// EXIST: { itemText: "DeprecatedSinceKotlin", tailText: " (kotlin)" }
// EXIST_NATIVE_ONLY: { itemText: "FreezingIsDeprecated", tailText: " (kotlin.native)" }
// EXIST_COMMON_ONLY: { itemText: "FreezingIsDeprecated", tailText: " (kotlin.native)" }
// EXIST_JAVA_ONLY: { itemText: "Deprecated", tailText: " (java.lang)" }
// NOTHING_ELSE
