// "Make 'foo' 'final'" "true"
// COMPILER_ARGUMENTS: -XXLanguage:-ProhibitOpenValDeferredInitialization
open class Foo {
    <caret>open val foo: Int
        get() = field

    init {
        foo = 2
    }
}
