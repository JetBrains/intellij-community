// "class org.jetbrains.kotlin.idea.quickfix.MakeClassAnAnnotationClassFix" "false"
// ERROR: 'String' is not an annotation class
// K2_AFTER_ERROR: Illegal annotation class 'String'.
@String<caret> class foo {}
