// "Add 'data' modifier" "true"
// JVM_TARGET: 16
// WITH_STDLIB
// K2_AFTER_ERROR: MISSING_DEPENDENCY_SUPERCLASS
// K2_AFTER_ERROR: MISSING_DEPENDENCY_SUPERCLASS
// K2_AFTER_ERROR: MISSING_DEPENDENCY_SUPERCLASS
// K2_ERROR: NON_DATA_CLASS_JVM_RECORD
<caret>@JvmRecord
class Rec(val length: Double, val width: Double)
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddModifierFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddModifierFix