// "Add initializer" "true"
// ERROR: Val cannot be reassigned
// COMPILER_ARGUMENTS: -XXLanguage:+ProhibitMissedMustBeInitializedWhenThereIsNoPrimaryConstructor -XXLanguage:+ProhibitOpenValDeferredInitialization
// K2_AFTER_ERROR: VAL_REASSIGNMENT
// K2_ERROR: MUST_BE_INITIALIZED_OR_FINAL_OR_ABSTRACT
open class Foo {
    constructor()

    <caret>open val x: String

    init {
        x = ""
    }
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.InitializePropertyQuickFixFactory$AddInitializerFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.InitializePropertyQuickFixFactories$InitializePropertyModCommandAction