// "Make 'foo' 'final'" "true"
// COMPILER_ARGUMENTS: -XXLanguage:+ProhibitOpenValDeferredInitialization
open class Foo {
    <caret>open val foo: Int
        get() = field

    init {
        foo = 2
    }
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddModifierFixFE10
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddModifierFix