// "Migrate type parameter list syntax" "true"
// K2_ERROR: Type parameters must be placed before function name.

class Foo

fun Foo.f<caret><T>() {}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.MigrateTypeParameterListFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.MigrateTypeParameterListFix