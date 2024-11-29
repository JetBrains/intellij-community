// "Make '<init>' private" "true"
// PRIORITY: HIGH
// ACTION: Add 'val' or 'var' to parameter 'arg'
// ACTION: Convert to secondary constructor
// ACTION: Create test
// ACTION: Enable a trailing comma by default in the formatter
// ACTION: Make '<init>' private
// ACTION: Make 'PrivateType' public
// ACTION: Remove parameter 'arg'

private class PrivateType

class Foo(<caret>arg: PrivateType) {}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeVisibilityFix$ChangeToPrivateFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ChangeVisibilityFixFactories$ChangeToPrivateModCommandAction