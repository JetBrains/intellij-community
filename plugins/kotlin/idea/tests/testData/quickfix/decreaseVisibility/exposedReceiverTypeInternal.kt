// "Make 'foo' internal" "true"
// K2_ERROR: EXPOSED_RECEIVER_TYPE
internal class A

fun <caret>A.foo() {}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeVisibilityFix$ChangeToInternalFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ChangeVisibilityFixFactories$ChangeToInternalModCommandAction