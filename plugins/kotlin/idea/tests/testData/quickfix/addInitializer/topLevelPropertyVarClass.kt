// "Add initializer" "true"
// WITH_STDLIB
// K2_ERROR: MUST_BE_INITIALIZED
class A
<caret>var label: A
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.InitializePropertyQuickFixFactories$InitializePropertyModCommandAction