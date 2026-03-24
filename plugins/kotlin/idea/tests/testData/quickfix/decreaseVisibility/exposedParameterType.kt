// "Make 'foo' private" "true"
// PRIORITY: HIGH
// ACTION: Convert parameter to receiver
// ACTION: Enable a trailing comma by default in the formatter
// ACTION: Make 'PrivateType' public
// ACTION: Make 'foo' private
// ACTION: Remove parameter 'arg'
// K2_ERROR: 'public' function exposes its 'private-in-file' parameter type 'PrivateType'.

private class PrivateType

fun foo(<caret>arg: PrivateType) {}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeVisibilityFix$ChangeToPrivateFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ChangeVisibilityFixFactories$ChangeToPrivateModCommandAction