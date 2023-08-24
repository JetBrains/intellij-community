// "Make 'bar' 'final'" "true"
interface Foo {
    val bar: String
}

open class FooImpl : Foo {
    override var bar: String = ""
        <caret>private set
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddModifierFixFE10
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddModifierFix