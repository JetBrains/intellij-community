// "Remove @ from annotation argument" "true"
// DISABLE-ERRORS
@Suppress({ <caret>@x
y() }) fun test() {}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveAtFromAnnotationArgument
/* IGNORE_K2 */
// KT-72831
// KTIJ-31896