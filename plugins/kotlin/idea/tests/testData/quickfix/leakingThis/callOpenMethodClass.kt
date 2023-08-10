// "Make 'My' 'final'" "true"

open class My {

    init {
        <caret>init()
    }

    open fun init() {}
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddModifierFixFE10