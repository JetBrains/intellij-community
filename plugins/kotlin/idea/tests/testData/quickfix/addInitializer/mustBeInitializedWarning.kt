// "Add initializer" "true"
// COMPILER_ARGUMENTS: -XXLanguage:-ProhibitMissedMustBeInitializedWhenThereIsNoPrimaryConstructor
class Foo {
    constructor()
    constructor(x: Int)
    <caret>var x: String
        set(value) {}

    init {
        x = ""
    }
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.InitializePropertyQuickFixFactory$AddInitializerFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.fixes.InitializePropertyQuickFixFactories$addInitializerApplicator$1