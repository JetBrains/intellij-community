// "Migrate type parameter list syntax" "true"
fun f<caret><T>() {}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.MigrateTypeParameterListFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.MigrateTypeParameterListFix