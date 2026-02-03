// "Migrate type parameter list syntax" "true"

class Foo

fun Foo.f<caret><T>() {}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.MigrateTypeParameterListFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.MigrateTypeParameterListFix