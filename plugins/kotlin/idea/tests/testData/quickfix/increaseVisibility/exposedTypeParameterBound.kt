// "Make 'InternalString' public" "true"
// PRIORITY: HIGH
// ACTION: Create test
// ACTION: Enable a trailing comma by default in the formatter
// ACTION: Introduce import alias
// ACTION: Make 'InternalString' public
// ACTION: Make 'User' internal
// ACTION: Make 'User' private

internal open class InternalString

class User<T : <caret>User<T, InternalString>, R>
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeVisibilityFix$ChangeToPublicFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ChangeVisibilityFixFactories$ChangeToPublicModCommandAction