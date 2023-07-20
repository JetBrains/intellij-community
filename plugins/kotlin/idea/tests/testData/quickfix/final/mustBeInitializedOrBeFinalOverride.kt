// "Make 'foo' 'final'" "true"
// COMPILER_ARGUMENTS: -XXLanguage:+ProhibitOpenValDeferredInitialization
open class Base {
    open val foo: Int = 2
}

open class Foo : Base() {
    <caret>override val foo: Int
        get() = field

    init {
        foo = 2
    }
}
