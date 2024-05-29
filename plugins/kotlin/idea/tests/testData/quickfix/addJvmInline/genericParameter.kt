// "Add '@JvmInline' annotation" "true"
// WITH_STDLIB
// COMPILER_ARGUMENTS: -XXLanguage:+GenericInlineClassParameter
<caret>value class VC(val i: Int)

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddJvmInlineAnnotationFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddJvmInlineAnnotationFix