// "Remove @JvmOverloads annotation" "true"
// WITH_STDLIB
// K2_ERROR: OVERLOADS_ANNOTATION_CLASS_CONSTRUCTOR_ERROR

annotation class A <caret>@JvmOverloads constructor(val x: Int = 1)
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.inspections.RemoveAnnotationFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.inspections.RemoveAnnotationFix