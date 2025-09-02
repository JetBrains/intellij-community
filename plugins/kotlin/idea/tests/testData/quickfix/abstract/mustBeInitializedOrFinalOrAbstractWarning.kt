// "Make 'x' 'abstract'" "true"
// ERROR: Val cannot be reassigned
// K2_AFTER_ERROR: 'val' cannot be reassigned.
// COMPILER_ARGUMENTS: -XXLanguage:+ProhibitMissedMustBeInitializedWhenThereIsNoPrimaryConstructor -XXLanguage:+ProhibitOpenValDeferredInitialization
open class Foo {
    constructor()

    <caret>open val x: String

    init {
        x = ""
    }
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddModifierFixMpp
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddModifierFixMpp