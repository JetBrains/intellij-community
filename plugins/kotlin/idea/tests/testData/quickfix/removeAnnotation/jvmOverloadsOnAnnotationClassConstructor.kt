// "Remove @JvmOverloads annotation" "true"
// IGNORE_FIR
// WITH_STDLIB

annotation class A <caret>@JvmOverloads constructor(val x: Int = 1)
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.inspections.RemoveAnnotationFix