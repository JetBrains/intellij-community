// "Make 'fancy' an annotation class" "true"
class fancy

@fancy<caret> class foo {}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.MakeClassAnAnnotationClassFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.MakeClassAnAnnotationClassFix