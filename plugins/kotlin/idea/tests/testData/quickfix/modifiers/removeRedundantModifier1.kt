// "Remove redundant 'open' modifier" "true"
abstract class B() {
    abstract fun foo()
}

abstract class A() : B() {
    <caret>open abstract override fun foo()
}


// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveModifierFixBase