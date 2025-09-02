// "Make 'bar' 'final'" "true"
interface Foo {
    val bar: String
}

open class FooImpl : Foo {
    override var bar: String = ""
        <caret>private set
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddModifierFixMpp
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddModifierFixMpp