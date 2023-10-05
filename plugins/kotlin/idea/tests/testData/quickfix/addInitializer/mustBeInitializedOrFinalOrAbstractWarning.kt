// "Add initializer" "true"
// ERROR: Val cannot be reassigned
// COMPILER_ARGUMENTS: -XXLanguage:+ProhibitMissedMustBeInitializedWhenThereIsNoPrimaryConstructor -XXLanguage:+ProhibitOpenValDeferredInitialization
open class Foo {
    constructor()

    <caret>open val x: String

    init {
        x = ""
    }
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.InitializePropertyQuickFixFactory$AddInitializerFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.fixes.InitializePropertyQuickFixFactories$addInitializerApplicator$1