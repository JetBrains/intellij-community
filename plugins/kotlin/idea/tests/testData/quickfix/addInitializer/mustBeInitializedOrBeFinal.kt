// "Add initializer" "true"
// ERROR: Val cannot be reassigned
// COMPILER_ARGUMENTS: -XXLanguage:+ProhibitOpenValDeferredInitialization
open class Foo {
    <caret>open val foo: Int
        get() = field

    init {
        foo = 2
    }
}
