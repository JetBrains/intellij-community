// "Make 'fancy' an annotation class" "true"
// K2_ERROR: Illegal annotation class 'fancy'.
class fancy

@fancy<caret> class foo {}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.MakeClassAnAnnotationClassFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.MakeClassAnAnnotationClassFix