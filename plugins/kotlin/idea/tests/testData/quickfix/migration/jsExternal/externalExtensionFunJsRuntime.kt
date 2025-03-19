// "Fix with 'asDynamic'" "true"
// JS

class A

<caret>external fun A.bar(): Unit = noImpl

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.migration.MigrateExternalExtensionFix