// "Add initializer" "true"
// WITH_STDLIB
// K2_ERROR: Property must be initialized.
class A
<caret>var label: A
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.InitializePropertyQuickFixFactory$AddInitializerFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.InitializePropertyQuickFixFactories$InitializePropertyModCommandAction