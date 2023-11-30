// "Add initializer" "true"
// WITH_STDLIB
class A
<caret>var label: A
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.InitializePropertyQuickFixFactory$AddInitializerFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.fixes.InitializePropertyQuickFixFactories$addInitializerApplicator$1