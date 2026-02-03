// "class org.jetbrains.kotlin.idea.quickfix.MakeClassAnAnnotationClassFix" "false"
// K2_ACTION: "class org.jetbrains.kotlin.idea.quickfix.AddModifierFixMpp" "false"
// ERROR: 'String' is not an annotation class
// K2_AFTER_ERROR: Illegal annotation class 'String'.
@String<caret> class foo {}
