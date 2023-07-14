// "Fix with 'asDynamic'" "true"
// JS

external class B 

@native<caret>Invoke
fun B.baz(a: B)
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.migration.MigrateExternalExtensionFix