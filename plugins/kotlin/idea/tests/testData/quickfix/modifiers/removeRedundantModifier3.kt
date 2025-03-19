// "Remove redundant 'open' modifier" "true"
abstract class B() {
    abstract fun foo()
}

abstract class A() : B() {
    abstract override <caret>open fun foo()
}


// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveModifierFixBase
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveModifierFixBase