// "Add '@JvmInline' annotation" "true"
// WITH_STDLIB
// COMPILER_ARGUMENTS: -XXLanguage:+GenericInlineClassParameter
// K2_ERROR: Value classes without '@JvmInline' annotation are not yet supported.
<caret>value class VC(val i: Int)

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddJvmInlineAnnotationFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddJvmInlineAnnotationFix