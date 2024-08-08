// "Make '<init>' private" "true"
// ACTION: Convert to secondary constructor
// ACTION: Create test
// ACTION: Enable a trailing comma by default in the formatter
// ACTION: Make 'PrivateType' public
// ACTION: Move to class body
// IGNORE_K1
// ERROR: 'public' function exposes its 'private-in-file' parameter type PrivateType

private class PrivateType

class Foo(private val <caret>arg: PrivateType) {}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ChangeVisibilityFixFactories$ChangeToPrivateModCommandAction