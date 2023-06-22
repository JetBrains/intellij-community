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
