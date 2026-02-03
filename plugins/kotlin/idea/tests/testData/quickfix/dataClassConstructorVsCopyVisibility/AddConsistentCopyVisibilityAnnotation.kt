// IGNORE_K1
// "Add '@ConsistentCopyVisibility' annotation" "true"
data class Foo private<caret> constructor(val x: Int)

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddAnnotationFix