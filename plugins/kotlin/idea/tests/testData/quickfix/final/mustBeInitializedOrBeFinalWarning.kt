// "Make 'foo' 'final'" "true"
// COMPILER_ARGUMENTS: -XXLanguage:-ProhibitOpenValDeferredInitialization
open class Foo {
    open <caret>val foo: Int
        get() = field

    init {
        foo = 2
    }
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddModifierFixMpp
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddModifierFix