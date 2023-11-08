// "Add initializer" "true"
class Foo {
    <caret>var foo: Int
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.InitializePropertyQuickFixFactory$AddInitializerFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.fixes.InitializePropertyQuickFixFactories$addInitializerApplicator$1