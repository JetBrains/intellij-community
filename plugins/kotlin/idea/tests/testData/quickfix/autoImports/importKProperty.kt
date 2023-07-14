// "Import class 'KProperty'" "true"
// WITH_STDLIB

fun foo(x: <caret>KProperty<Int>) {}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ImportFix