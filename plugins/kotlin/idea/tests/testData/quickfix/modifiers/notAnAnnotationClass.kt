// "Make 'fancy' an annotation class" "true"
class fancy

@fancy<caret> class foo {}

/* IGNORE_FIR */
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.MakeClassAnAnnotationClassFix