// "Add initializer" "true"
class A {
    <caret>var n: Int
        get() = 1
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.InitializePropertyQuickFixFactory$AddInitializerFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.fixes.InitializePropertyQuickFixFactories$addInitializerApplicator$1