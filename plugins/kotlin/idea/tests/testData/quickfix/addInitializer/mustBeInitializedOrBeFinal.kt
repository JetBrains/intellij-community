// "Add initializer" "true"
// ERROR: Val cannot be reassigned
// COMPILER_ARGUMENTS: -XXLanguage:+ProhibitOpenValDeferredInitialization
// K2_AFTER_ERROR: VAL_REASSIGNMENT
// K2_ERROR: MUST_BE_INITIALIZED_OR_BE_FINAL
open class Foo {
    <caret>open val foo: Int
        get() = field

    init {
        foo = 2
    }
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.InitializePropertyQuickFixFactory$AddInitializerFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.InitializePropertyQuickFixFactories$InitializePropertyModCommandAction