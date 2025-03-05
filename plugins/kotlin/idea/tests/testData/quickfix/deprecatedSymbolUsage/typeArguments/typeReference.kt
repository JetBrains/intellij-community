// "Replace with 'New<String, Int>'" "true"

@Deprecated("Use New", replaceWith = ReplaceWith("New<T, U>"))
class Old<T, U>

class New<T, U>

fun foo(): <caret>Old<String, Int>? = null
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.replaceWith.DeprecatedSymbolUsageFix
// IGNORE_K2