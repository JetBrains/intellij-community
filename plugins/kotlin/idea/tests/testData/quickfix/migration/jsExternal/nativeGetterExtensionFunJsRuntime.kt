// "Fix with 'asDynamic'" "true"
// JS

external class B 

@<caret>nativeGetter
fun B.boo(i: Int): B?
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.migration.MigrateExternalExtensionFix