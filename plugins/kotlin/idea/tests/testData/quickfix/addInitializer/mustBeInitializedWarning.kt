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
