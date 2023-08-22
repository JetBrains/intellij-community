// "Add initializer" "true"
// ERROR: Val cannot be reassigned
// COMPILER_ARGUMENTS: -XXLanguage:-ProhibitOpenValDeferredInitialization
open class Foo {
    <caret>open val foo: Int
        get() = field

    init {
        foo = 2
    }
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.InitializePropertyQuickFixFactory$AddInitializerFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinApplicatorBasedQuickFix