// "Fix with 'asDynamic'" "true"
// JS

external class B 

@<caret>nativeSetter
fun B.boo(i: Int, v: B)
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.migration.MigrateExternalExtensionFix