// IGNORE_K1
// COMPILER_ARGUMENTS: -XXLanguage:+DataClassCopyRespectsConstructorVisibility
// "Remove annotation" "true"
@ConsistentCopyVisibility<caret>
data class Foo private constructor(val x: Int)
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.inspections.RemoveAnnotationFix