// "Make 'My' 'final'" "true"

open class My {
    init {
        register(<caret>this)
    }
}

fun register(my: My) {}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddModifierFixFE10