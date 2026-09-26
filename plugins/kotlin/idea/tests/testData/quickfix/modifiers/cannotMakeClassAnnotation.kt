// "class org.jetbrains.kotlin.idea.quickfix.MakeClassAnAnnotationClassFix" "false"
// K2_ACTION: "class org.jetbrains.kotlin.idea.quickfix.AddModifierFixMpp" "false"
// ERROR: 'String' is not an annotation class
// K2_AFTER_ERROR: NOT_AN_ANNOTATION_CLASS
// K2_ERROR: NOT_AN_ANNOTATION_CLASS
@String<caret> class foo {}
