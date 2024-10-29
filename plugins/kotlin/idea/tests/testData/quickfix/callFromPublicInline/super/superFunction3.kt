// "Remove 'inline' modifier" "true"
open class Base {
    fun baseFun(param: Any) {}
}

open class Derived : Base() {
    inline val prop: Unit
        get() {
            <caret>super.baseFun("123")
        }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveModifierFixBase
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveModifierFixBase