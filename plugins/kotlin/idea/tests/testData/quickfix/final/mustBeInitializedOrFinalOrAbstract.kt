// "Make 'x' 'final'" "true"
// COMPILER_ARGUMENTS: -XXLanguage:+ProhibitMissedMustBeInitializedWhenThereIsNoPrimaryConstructor -XXLanguage:+ProhibitOpenValDeferredInitialization
open class Foo {
    constructor()

    <caret>open val x: String

    init {
        x = ""
    }
}
