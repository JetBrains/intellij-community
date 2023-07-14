// "Make 'foo' private" "true"
internal class A

fun <caret>A.foo() {}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeVisibilityFix$ChangeToPrivateFix