// "Make 'inlineFun' internal" "true"
open class Base {
    fun baseFun(param: Any) {}
}

open class Derived : Base() {
    inline fun inlineFun() {
        <caret>super.baseFun("123")
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeVisibilityFix