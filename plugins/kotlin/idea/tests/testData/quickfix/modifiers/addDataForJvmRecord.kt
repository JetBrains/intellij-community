// "Add 'data' modifier" "true"
// JVM_TARGET: 16
// WITH_STDLIB
// K2_AFTER_ERROR: Cannot access 'Record' which is a supertype of 'Rec'. Check your module classpath for missing or conflicting dependencies.
<caret>@JvmRecord
class Rec(val length: Double, val width: Double)
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddModifierFixFE10
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddModifierFix